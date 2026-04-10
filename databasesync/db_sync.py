import os
import sys
import time
from datetime import datetime, date, timedelta
import pyodbc

# ================= CONFIG =================
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

TARGET_VIEW = "[dbo].[BIO_USERMAST]"

SYNC_INTERVAL_SECONDS = 300

LOG_FILE = "sync_debug.log"

# ==========================================


def log(msg):
    line = f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | {msg}"
    print(line)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(line + "\n")


def connect():
    return pyodbc.connect(LOCAL_CONN_STR)


# ================= DELETE =================
def run_delete(cursor):
    sql = f"""
    SET NOCOUNT ON;

    WITH DeletedIds AS (
        SELECT DISTINCT TRY_CAST(Workman_ID AS BIGINT) AS Workman_ID
        FROM {SOURCE_DELETED_WORKMEN}
        WHERE Workman_ID IS NOT NULL
    )
    UPDATE t
    SET ISDELETED = 1,
        DEL_DATE = GETDATE()
    OUTPUT inserted.ID, deleted.ISDELETED, inserted.ISDELETED
    FROM {TARGET_VIEW} t
    JOIN DeletedIds d
        ON TRY_CAST(t.ID AS BIGINT) = d.Workman_ID
    WHERE ISNULL(t.ISDELETED, 0) <> 1
       OR t.DEL_DATE IS NULL;
    """

    cursor.execute(sql)
    rows = cursor.fetchall()

    for r in rows:
        log(f"DELETE | ID={r[0]} | {r[1]} -> {r[2]}")

    return len(rows)


# ================= UPDATE (FIXED) =================
def run_update(cursor):
    sql = f"""
    SET NOCOUNT ON;

    DECLARE @changes TABLE (
        user_id BIGINT,
        old_verify INT,
        new_verify INT,
        reason VARCHAR(50)
    );

    WITH LatestStatus AS (
        SELECT
            TRY_CAST(Workman_ID AS BIGINT) AS Workman_ID,
            TRY_CAST(Record_Disabled AS INT) AS Record_Disabled,
            GatePassValidFrom,
            GatePassValidUpto,
            ROW_NUMBER() OVER (
                PARTITION BY Workman_ID
                ORDER BY lastactivationdate DESC
            ) rn
        FROM {SOURCE_WORKMEN_MASTER}
    ),
    FinalStatus AS (
        SELECT
            Workman_ID,
            CASE
                WHEN Record_Disabled = 1 THEN 0
                WHEN GETDATE() BETWEEN GatePassValidFrom AND GatePassValidUpto THEN 1
                ELSE 0
            END AS TargetStatus,
            CASE
                WHEN Record_Disabled = 1 THEN 'record_disabled'
                WHEN GETDATE() BETWEEN GatePassValidFrom AND GatePassValidUpto THEN 'valid'
                ELSE 'expired'
            END AS reason,
            rn
        FROM LatestStatus
    )

    UPDATE t
    SET
        t.Verify = s.TargetStatus,
        t.UPD_DATE = GETDATE()
    OUTPUT
        inserted.ID,
        ISNULL(deleted.Verify, -1),
        ISNULL(inserted.Verify, -1),
        s.reason
    INTO @changes
    FROM {TARGET_VIEW} t
    JOIN FinalStatus s
        ON TRY_CAST(t.ID AS BIGINT) = s.Workman_ID
    WHERE s.rn = 1
      AND (
            t.Verify IS NULL
            OR t.Verify <> s.TargetStatus
          );

    SELECT * FROM @changes;
    """

    cursor.execute(sql)
    rows = cursor.fetchall()

    for r in rows:
        log(f"UPDATE | ID={r[0]} | {r[1]} -> {r[2]} | reason={r[3]}")

    return len(rows)


# ================= MAIN LOOP =================
def main():
    log("===== SERVICE START =====")

    while True:
        conn = None
        start = time.time()

        try:
            conn = connect()
            cursor = conn.cursor()

            log("RUN START")

            del_count = run_delete(cursor)
            upd_count = run_update(cursor)

            conn.commit()

            duration = int((time.time() - start) * 1000)

            log(f"SUMMARY | deleted={del_count} | updated={upd_count} | time={duration}ms")

        except Exception as e:
            if conn:
                conn.rollback()
            log(f"ERROR | {str(e)}")

        finally:
            if conn:
                conn.close()

        time.sleep(SYNC_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()