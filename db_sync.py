import os
import sys
import time
from datetime import date, datetime, timedelta

import pyodbc


LOCAL_CONN_STR = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost,1433;"
    "DATABASE=IDSL_NTPC_CLIMS;"
    "UID=sa;"
    "PWD=T@123;"
    "Encrypt=yes;"
    "TrustServerCertificate=yes;"
)

SOURCE_WORKMEN_MASTER = "[10.0.8.226].[CLIMS].[dbo].[Patratu_WorkmenMaster]"
SOURCE_DELETED_WORKMEN = "[10.0.8.226].[CLIMS].[dbo].[Patratu_DeletedWorkmen]"

TARGET_SCHEMA = "dbo"
TARGET_VIEW_NAME = "BIO_USERMAST"
SOURCE_WORKMAN_ID_COLUMN_NAME = "Workman_ID"
TARGET_ID_COLUMN_NAME = "ID"
TARGET_ISDELETED_COLUMN_NAME = "ISDELETED"
TARGET_DEL_DATE_COLUMN_NAME = "DEL_DATE"
TARGET_VERIFY_COLUMN_NAME = "Verify"
TARGET_UPD_DATE_COLUMN_NAME = "UPD_DATE"

SYNC_INTERVAL_SECONDS = 5 * 60
CONNECT_TIMEOUT_SECONDS = 15
QUERY_TIMEOUT_SECONDS = 120

BASE_DIR = os.path.dirname(
    os.path.abspath(sys.executable if getattr(sys, "frozen", False) else __file__)
)
LOG_FILE_PATH = os.path.join(BASE_DIR, "connectivitylog.txt")
TIMESTAMP_FORMAT = "%Y-%m-%d %H:%M:%S"


def quote_name(name):
    return f"[{name}]"


SOURCE_WORKMAN_ID_COLUMN = quote_name(SOURCE_WORKMAN_ID_COLUMN_NAME)
TARGET_ID_COLUMN = quote_name(TARGET_ID_COLUMN_NAME)
TARGET_ISDELETED_COLUMN = quote_name(TARGET_ISDELETED_COLUMN_NAME)
TARGET_DEL_DATE_COLUMN = quote_name(TARGET_DEL_DATE_COLUMN_NAME)
TARGET_VERIFY_COLUMN = quote_name(TARGET_VERIFY_COLUMN_NAME)
TARGET_UPD_DATE_COLUMN = quote_name(TARGET_UPD_DATE_COLUMN_NAME)

RUN_COUNTER = 0


def now_timestamp_text():
    return datetime.now().strftime(TIMESTAMP_FORMAT)


def sanitize_value(value):
    if value is None:
        return ""
    text = str(value)
    return text.replace("\r", " ").replace("\n", " ").strip()


def write_log_line(line):
    print(line, flush=True)
    with open(LOG_FILE_PATH, "a", encoding="utf-8") as log_file:
        log_file.write(line + "\n")


def format_log_line(event, run_id=None, fields=None):
    parts = [now_timestamp_text(), event]
    if run_id:
        parts.append(f"run_id={sanitize_value(run_id)}")
    if fields:
        for key, value in fields:
            parts.append(f"{key}={sanitize_value(value)}")
    return " | ".join(parts)


def log_event(event, run_id=None, fields=None):
    write_log_line(format_log_line(event, run_id=run_id, fields=fields))


def prune_old_log_lines():
    if not os.path.exists(LOG_FILE_PATH):
        return

    cutoff_date = date.today() - timedelta(days=2)
    kept_lines = []
    with open(LOG_FILE_PATH, "r", encoding="utf-8", errors="ignore") as log_file:
        for line in log_file:
            raw = line.rstrip("\n")
            if len(raw) < 19:
                continue
            try:
                line_date = datetime.strptime(raw[:19], TIMESTAMP_FORMAT).date()
            except ValueError:
                continue
            if line_date >= cutoff_date:
                kept_lines.append(raw)

    with open(LOG_FILE_PATH, "w", encoding="utf-8") as log_file:
        if kept_lines:
            log_file.write("\n".join(kept_lines) + "\n")


def next_run_id():
    global RUN_COUNTER
    RUN_COUNTER += 1
    return datetime.now().strftime("%Y%m%d_%H%M%S") + f"_{RUN_COUNTER:03d}"


def connect():
    connection = pyodbc.connect(LOCAL_CONN_STR, timeout=CONNECT_TIMEOUT_SECONDS)
    connection.timeout = QUERY_TIMEOUT_SECONDS
    return connection


def format_target_view(view_name):
    return f"{quote_name(TARGET_SCHEMA)}.{quote_name(view_name)}"


def build_delete_sql(target_view):
    return f"""
SET NOCOUNT ON;

DECLARE @changes TABLE (
    user_id BIGINT,
    old_isdeleted INT,
    new_isdeleted INT
);

WITH DeletedIds AS (
    SELECT DISTINCT TRY_CAST({SOURCE_WORKMAN_ID_COLUMN} AS BIGINT) AS Workman_ID
    FROM {SOURCE_DELETED_WORKMEN}
    WHERE {SOURCE_WORKMAN_ID_COLUMN} IS NOT NULL
)
UPDATE target_rows
SET {TARGET_ISDELETED_COLUMN} = 1,
    {TARGET_DEL_DATE_COLUMN} = GETDATE()
OUTPUT
    TRY_CAST(inserted.{TARGET_ID_COLUMN} AS BIGINT),
    ISNULL(TRY_CAST(deleted.{TARGET_ISDELETED_COLUMN} AS INT), 0),
    ISNULL(TRY_CAST(inserted.{TARGET_ISDELETED_COLUMN} AS INT), 0)
INTO @changes(user_id, old_isdeleted, new_isdeleted)
FROM {target_view} AS target_rows
INNER JOIN DeletedIds AS source_rows
    ON TRY_CAST(target_rows.{TARGET_ID_COLUMN} AS BIGINT) = source_rows.Workman_ID
WHERE source_rows.Workman_ID IS NOT NULL
  AND (
      ISNULL(TRY_CAST(target_rows.{TARGET_ISDELETED_COLUMN} AS INT), 0) <> 1
      OR target_rows.{TARGET_DEL_DATE_COLUMN} IS NULL
  );

SELECT user_id, old_isdeleted, new_isdeleted
FROM @changes
ORDER BY user_id;
"""


def build_update_status_sql(target_view, update_upd_date):
    set_clauses = [f"{TARGET_VERIFY_COLUMN} = source_rows.TargetStatus"]
    if update_upd_date:
        set_clauses.append(f"{TARGET_UPD_DATE_COLUMN} = GETDATE()")
    set_clause_sql = ",\n    ".join(set_clauses)

    return f"""
SET NOCOUNT ON;

DECLARE @changes TABLE (
    user_id BIGINT,
    old_verify INT,
    new_verify INT,
    reason VARCHAR(40)
);

WITH LatestStatus AS (
    SELECT
        TRY_CAST({SOURCE_WORKMAN_ID_COLUMN} AS BIGINT) AS Workman_ID,
        TRY_CAST([Record_Disabled] AS INT) AS Record_Disabled,
        [GatePassValidFrom],
        [GatePassValidUpto],
        ROW_NUMBER() OVER (
            PARTITION BY {SOURCE_WORKMAN_ID_COLUMN}
            ORDER BY
                CASE WHEN [lastactivationdate] IS NULL THEN 1 ELSE 0 END,
                [lastactivationdate] DESC,
                CASE WHEN [lastdeactivationdate] IS NULL THEN 1 ELSE 0 END,
                [lastdeactivationdate] DESC
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
        CASE
            WHEN Record_Disabled = 1 THEN 'record_disabled'
            WHEN GETDATE() BETWEEN [GatePassValidFrom] AND [GatePassValidUpto] THEN 'gatepass_valid'
            ELSE 'gatepass_invalid'
        END AS ReasonCode,
        row_num
    FROM LatestStatus
)
UPDATE target_rows
SET {set_clause_sql}
OUTPUT
    TRY_CAST(inserted.{TARGET_ID_COLUMN} AS BIGINT),
    ISNULL(TRY_CAST(deleted.{TARGET_VERIFY_COLUMN} AS INT), -1),
    ISNULL(TRY_CAST(inserted.{TARGET_VERIFY_COLUMN} AS INT), -1),
    source_rows.ReasonCode
INTO @changes(user_id, old_verify, new_verify, reason)
FROM {target_view} AS target_rows
INNER JOIN ResolvedStatus AS source_rows
    ON TRY_CAST(target_rows.{TARGET_ID_COLUMN} AS BIGINT) = source_rows.Workman_ID
WHERE source_rows.row_num = 1
  AND ISNULL(TRY_CAST(target_rows.{TARGET_VERIFY_COLUMN} AS INT), -1) <> source_rows.TargetStatus;

SELECT user_id, old_verify, new_verify, reason
FROM @changes
ORDER BY user_id;
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
    similar_views = [row[0] for row in cursor.execute(similar_view_sql, TARGET_SCHEMA).fetchall()]
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
            raise RuntimeError(f"ID column [{TARGET_ID_COLUMN_NAME}] not found in {target_view}")
        if TARGET_ISDELETED_COLUMN_NAME.lower() not in available_columns:
            raise RuntimeError(f"ISDELETED column [{TARGET_ISDELETED_COLUMN_NAME}] not found in {target_view}")
        if TARGET_DEL_DATE_COLUMN_NAME.lower() not in available_columns:
            raise RuntimeError(f"DEL_DATE column [{TARGET_DEL_DATE_COLUMN_NAME}] not found in {target_view}")

        cursor.execute(build_delete_sql(target_view))
        delete_rows = cursor.fetchall()
        deleted_events = [
            {
                "user_id": int(row.user_id) if row.user_id is not None else None,
                "old_isdeleted": int(row.old_isdeleted) if row.old_isdeleted is not None else 0,
                "new_isdeleted": int(row.new_isdeleted) if row.new_isdeleted is not None else 1,
            }
            for row in delete_rows
            if row.user_id is not None
        ]

        verify_sync_enabled = TARGET_VERIFY_COLUMN_NAME.lower() in available_columns
        upd_date_sync_enabled = TARGET_UPD_DATE_COLUMN_NAME.lower() in available_columns
        status_events = []
        if verify_sync_enabled:
            cursor.execute(build_update_status_sql(target_view, upd_date_sync_enabled))
            status_rows = cursor.fetchall()
            status_events = [
                {
                    "user_id": int(row.user_id) if row.user_id is not None else None,
                    "old_verify": int(row.old_verify) if row.old_verify is not None else -1,
                    "new_verify": int(row.new_verify) if row.new_verify is not None else -1,
                    "reason": sanitize_value(row.reason) if row.reason is not None else "",
                }
                for row in status_rows
                if row.user_id is not None
            ]

        connection.commit()
        return {
            "target_view": target_view,
            "verify_sync_enabled": verify_sync_enabled,
            "upd_date_sync_enabled": upd_date_sync_enabled,
            "status_events": status_events,
            "deleted_events": deleted_events,
        }
    finally:
        cursor.close()


def main():
    prune_old_log_lines()
    log_event("SERVICE_START", fields=[("target", TARGET_VIEW_NAME), ("interval", f"{SYNC_INTERVAL_SECONDS}s")])

    while True:
        connection = None
        run_id = next_run_id()
        started_at = time.time()
        log_event(
            "RUN_START",
            run_id=run_id,
            fields=[("target", f"{TARGET_SCHEMA}.{TARGET_VIEW_NAME}"), ("interval", f"{SYNC_INTERVAL_SECONDS}s")],
        )

        try:
            connection = connect()
            identity_cursor = connection.cursor()
            try:
                server_name, db_name, login_name = get_identity(identity_cursor)
            finally:
                identity_cursor.close()

            log_event(
                "CONNECTED",
                run_id=run_id,
                fields=[("server", server_name), ("db", db_name), ("login", login_name)],
            )

            result = run_sync_once(connection)

            if not result["verify_sync_enabled"]:
                log_event(
                    "RUN_NOTE",
                    run_id=run_id,
                    fields=[("message", f"Verify column [{TARGET_VERIFY_COLUMN_NAME}] not found; verify sync skipped")],
                )
            elif not result["upd_date_sync_enabled"]:
                log_event(
                    "RUN_NOTE",
                    run_id=run_id,
                    fields=[("message", f"UPD_DATE column [{TARGET_UPD_DATE_COLUMN_NAME}] not found; upd_date update skipped")],
                )

            enabled_count = 0
            disabled_count = 0
            deleted_count = len(result["deleted_events"])

            for event in result["status_events"]:
                action = "ENABLE" if event["new_verify"] == 1 else "DISABLE"
                if action == "ENABLE":
                    enabled_count += 1
                else:
                    disabled_count += 1
                log_event(
                    "EVENT",
                    run_id=run_id,
                    fields=[
                        ("user_id", event["user_id"]),
                        ("action", action),
                        ("old_verify", event["old_verify"]),
                        ("new_verify", event["new_verify"]),
                        ("reason", event["reason"]),
                    ],
                )

            for event in result["deleted_events"]:
                log_event(
                    "EVENT",
                    run_id=run_id,
                    fields=[
                        ("user_id", event["user_id"]),
                        ("action", "DELETE"),
                        ("old_isdeleted", event["old_isdeleted"]),
                        ("new_isdeleted", event["new_isdeleted"]),
                    ],
                )

            total_events = enabled_count + disabled_count + deleted_count
            duration_ms = int((time.time() - started_at) * 1000)
            log_event(
                "RUN_SUMMARY",
                run_id=run_id,
                fields=[
                    ("total_events", total_events),
                    ("enabled", enabled_count),
                    ("disabled", disabled_count),
                    ("deleted", deleted_count),
                    ("duration_ms", duration_ms),
                ],
            )
        except KeyboardInterrupt:
            log_event("RUN_STOPPED", run_id=run_id, fields=[("message", "Stopped by user")])
            raise
        except Exception as exc:
            if connection is not None:
                try:
                    connection.rollback()
                except Exception:
                    pass
            duration_ms = int((time.time() - started_at) * 1000)
            log_event(
                "RUN_FAILURE",
                run_id=run_id,
                fields=[
                    ("step", "sync"),
                    ("error", str(exc)),
                    ("duration_ms", duration_ms),
                ],
            )
        finally:
            if connection is not None:
                connection.close()

        prune_old_log_lines()
        time.sleep(SYNC_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
