import time
from datetime import datetime

import pyodbc


LOCAL_CONN_STR = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost\\SQLEXPRESS;"
    "DATABASE=IDSL_NTPC_CLIMS;"
    "Trusted_Connection=yes;"
    "Encrypt=yes;"
    "TrustServerCertificate=yes;"
)

SOURCE_WORKMEN_MASTER = "[10.0.8.226].[CLIMS].[dbo].[Patratu_WorkmenMaster_IDSL]"
SOURCE_DELETED_WORKMEN = "[10.0.8.226].[CLIMS].[dbo].[Patratu_DeletedWorkmen]"

TARGET_SCHEMA = "dbo"
TARGET_VIEW_NAME = "BIO_USERMAST"
SOURCE_WORKMAN_ID_COLUMN_NAME = "Workman_ID"
TARGET_ID_COLUMN_NAME = "ID"
TARGET_VERIFY_COLUMN_NAME = "Verify"

SYNC_INTERVAL_SECONDS = 5 * 60
CONNECT_TIMEOUT_SECONDS = 15
QUERY_TIMEOUT_SECONDS = 120


def quote_name(name):
    return f"[{name}]"


SOURCE_WORKMAN_ID_COLUMN = quote_name(SOURCE_WORKMAN_ID_COLUMN_NAME)
TARGET_ID_COLUMN = quote_name(TARGET_ID_COLUMN_NAME)
TARGET_VERIFY_COLUMN = quote_name(TARGET_VERIFY_COLUMN_NAME)

def log(message):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"{timestamp} | {message}", flush=True)


def connect():
    connection = pyodbc.connect(LOCAL_CONN_STR, timeout=CONNECT_TIMEOUT_SECONDS)
    connection.timeout = QUERY_TIMEOUT_SECONDS
    return connection


def format_target_view(view_name):
    return f"{quote_name(TARGET_SCHEMA)}.{quote_name(view_name)}"


def build_delete_sql(target_view):
    return f"""
SET NOCOUNT ON;

WITH DeletedIds AS (
    SELECT DISTINCT TRY_CAST({SOURCE_WORKMAN_ID_COLUMN} AS BIGINT) AS Workman_ID
    FROM {SOURCE_DELETED_WORKMEN}
    WHERE {SOURCE_WORKMAN_ID_COLUMN} IS NOT NULL
)
DELETE target_rows
FROM {target_view} AS target_rows
INNER JOIN DeletedIds AS source_rows
    ON TRY_CAST(target_rows.{TARGET_ID_COLUMN} AS BIGINT) = source_rows.Workman_ID
WHERE source_rows.Workman_ID IS NOT NULL;

SELECT @@ROWCOUNT AS affected_rows;
"""


def build_update_status_sql(target_view):
    return f"""
SET NOCOUNT ON;

WITH LatestStatus AS (
    SELECT
        TRY_CAST({SOURCE_WORKMAN_ID_COLUMN} AS BIGINT) AS Workman_ID,
        TRY_CAST([Record_Disabled] AS INT) AS Record_Disabled,
        [GatePassValidFrom],
        [GatePassValidUpto],
        ROW_NUMBER() OVER (
            PARTITION BY {SOURCE_WORKMAN_ID_COLUMN}
            ORDER BY
                CASE WHEN [last_update_date] IS NULL THEN 1 ELSE 0 END,
                [last_update_date] DESC,
                CASE WHEN [Record_Creation_Date] IS NULL THEN 1 ELSE 0 END,
                [Record_Creation_Date] DESC
        ) AS row_num
    FROM {SOURCE_WORKMEN_MASTER}
    WHERE {SOURCE_WORKMAN_ID_COLUMN} IS NOT NULL
      AND [Record_Disabled] IN (0, 1)
),
ResolvedStatus AS (
    SELECT
        Workman_ID,
        CASE
            WHEN Record_Disabled = 1 THEN 0
            WHEN GETDATE() BETWEEN [GatePassValidFrom] AND [GatePassValidUpto] THEN 1
            ELSE 0
        END AS TargetStatus,
        row_num
    FROM LatestStatus
)
UPDATE target_rows
SET {TARGET_VERIFY_COLUMN} = source_rows.TargetStatus
FROM {target_view} AS target_rows
INNER JOIN ResolvedStatus AS source_rows
    ON TRY_CAST(target_rows.{TARGET_ID_COLUMN} AS BIGINT) = source_rows.Workman_ID
WHERE source_rows.row_num = 1
  AND ISNULL(TRY_CAST(target_rows.{TARGET_VERIFY_COLUMN} AS INT), -1) <> source_rows.TargetStatus;

SELECT @@ROWCOUNT AS affected_rows;
"""


def get_identity(cursor):
    row = cursor.execute(
        "SELECT @@SERVERNAME AS server_name, DB_NAME() AS db_name, SUSER_SNAME() AS login_name"
    ).fetchone()
    return row.server_name, row.db_name, row.login_name


def get_view_metadata(cursor, view_name):
    view_sql = """
    SELECT v.object_id
    FROM sys.views AS v
    INNER JOIN sys.schemas AS s
        ON s.schema_id = v.schema_id
    WHERE s.name = ? AND v.name = ?;
    """
    view_row = cursor.execute(view_sql, TARGET_SCHEMA, view_name).fetchone()
    if view_row is None:
        return None

    column_sql = """
    SELECT c.name
    FROM sys.columns AS c
    WHERE c.object_id = ?
    ORDER BY c.column_id;
    """
    columns = [row[0] for row in cursor.execute(column_sql, view_row.object_id).fetchall()]
    return {
        "name": view_name,
        "type_desc": "VIEW",
        "columns": {column.lower() for column in columns},
    }


def resolve_target_view(cursor):
    metadata = get_view_metadata(cursor, TARGET_VIEW_NAME)
    if metadata is not None:
        return metadata

    similar_view_sql = """
    SELECT v.name
    FROM sys.views AS v
    INNER JOIN sys.schemas AS s
        ON s.schema_id = v.schema_id
    WHERE s.name = ? AND v.name LIKE '%USERMAST%'
    ORDER BY v.name;
    """
    similar_views = [
        row[0]
        for row in cursor.execute(similar_view_sql, TARGET_SCHEMA).fetchall()
    ]
    similar_views_text = ", ".join(similar_views) if similar_views else "<none>"
    raise RuntimeError(
        f"Target view not found in schema [{TARGET_SCHEMA}] | "
        f"checked={TARGET_VIEW_NAME} | similar_views={similar_views_text}"
    )


def run_sync_once(connection):
    cursor = connection.cursor()

    try:
        target_metadata = resolve_target_view(cursor)
        target_view = format_target_view(target_metadata["name"])
        available_columns = target_metadata["columns"]

        if TARGET_ID_COLUMN_NAME.lower() not in available_columns:
            raise RuntimeError(
                f"ID column [{TARGET_ID_COLUMN_NAME}] not found in {target_view}"
            )

        cursor.execute(build_delete_sql(target_view))
        deleted_rows = cursor.fetchone()[0]

        updated_rows = 0
        verify_sync_enabled = TARGET_VERIFY_COLUMN_NAME.lower() in available_columns
        if verify_sync_enabled:
            cursor.execute(build_update_status_sql(target_view))
            updated_rows = cursor.fetchone()[0]

        connection.commit()
        return target_view, deleted_rows, updated_rows, verify_sync_enabled
    finally:
        cursor.close()


def main():
    log("DB sync service started")
    log(
        f"Target view={TARGET_VIEW_NAME} | source_workman_id_column={SOURCE_WORKMAN_ID_COLUMN} | "
        f"target_id_column={TARGET_ID_COLUMN} | "
        f"verify_column={TARGET_VERIFY_COLUMN} | interval={SYNC_INTERVAL_SECONDS}s"
    )

    while True:
        connection = None

        try:
            connection = connect()
            identity_cursor = connection.cursor()
            try:
                server_name, db_name, login_name = get_identity(identity_cursor)
            finally:
                identity_cursor.close()
            log(
                f"Connected | server={server_name} | database={db_name} | login={login_name}"
            )

            target_view, deleted_rows, updated_rows, verify_sync_enabled = run_sync_once(
                connection
            )
            if not verify_sync_enabled:
                log(
                    f"Verify column [{TARGET_VERIFY_COLUMN_NAME}] not found in {target_view} | "
                    "verify sync skipped"
                )
            log(
                f"Sync completed | target_view={target_view} | "
                f"deleted_rows={deleted_rows} | updated_verify_rows={updated_rows}"
            )
        except KeyboardInterrupt:
            log("Sync stopped by user")
            raise
        except Exception as exc:
            if connection is not None:
                try:
                    connection.rollback()
                except Exception:
                    pass
            log(f"Sync failed | {exc}")
        finally:
            if connection is not None:
                connection.close()

        time.sleep(SYNC_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
