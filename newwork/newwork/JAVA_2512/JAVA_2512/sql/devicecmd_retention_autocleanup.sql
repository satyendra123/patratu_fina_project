/*
  DEVICECMD retention + auto-clean setup (SQL Server)
  ---------------------------------------------------
  Purpose:
  1) Prevent DEVICECMD from growing forever.
  2) Reduce chance of PRIMARY filegroup full (error 1105).
  3) Avoid manual cleanup query execution every time.

  Run this once in SSMS on the application DB (example: BIO_DATA_NTPC_CLIMS).
*/

SET NOCOUNT ON;
GO

PRINT 'Database: ' + DB_NAME();
PRINT 'Time: ' + CONVERT(VARCHAR(19), GETDATE(), 120);
GO

/* 1) Ensure PRIMARY row files can auto-grow */
DECLARE @growSql NVARCHAR(MAX) = N'';
SELECT @growSql = @growSql +
    N'ALTER DATABASE [' + DB_NAME() + N'] MODIFY FILE (NAME = N''' + df.name
    + N''', FILEGROWTH = 512MB, MAXSIZE = UNLIMITED);' + CHAR(10)
FROM sys.database_files df
WHERE df.type_desc = 'ROWS'
  AND df.data_space_id IN (
      SELECT data_space_id FROM sys.filegroups WHERE name = 'PRIMARY'
  );

IF LEN(@growSql) > 0
BEGIN
    PRINT 'Applying PRIMARY autogrowth settings...';
    EXEC sp_executesql @growSql;
END
GO

/* 2) Optional helper index for cleanup scans (safe if already exists) */
IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_DEVICECMD_RETENTION_SCAN'
      AND object_id = OBJECT_ID('dbo.DEVICECMD')
)
BEGIN
    CREATE NONCLUSTERED INDEX IX_DEVICECMD_RETENTION_SCAN
    ON dbo.DEVICECMD (
        IS_DEL_EXECUTED,
        DC_RES,
        DC_RESDATE,
        DC_cmd_date,
        DC_DATE
    )
    INCLUDE (DC_ID, CMD_DESC, SLNO);
END
GO

/* 3) Stored procedure: batched cleanup for completed rows */
IF OBJECT_ID('dbo.usp_devicecmd_retention_cleanup', 'P') IS NOT NULL
    DROP PROCEDURE dbo.usp_devicecmd_retention_cleanup;
GO

CREATE PROCEDURE dbo.usp_devicecmd_retention_cleanup
    @RetentionDays INT = 0,
    @BatchSize INT = 2000,
    @MaxLoops INT = 30
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @loop INT = 0;
    DECLARE @deleted INT = 1;
    DECLARE @totalDeleted INT = 0;

    IF @RetentionDays < 0 SET @RetentionDays = 0;
    IF @BatchSize < 100 SET @BatchSize = 100;
    IF @MaxLoops < 1 SET @MaxLoops = 1;

    WHILE (@loop < @MaxLoops AND @deleted > 0)
    BEGIN
        SET @loop = @loop + 1;

        DELETE TOP (@BatchSize)
        FROM dbo.DEVICECMD
        WHERE ISNULL(CONVERT(INT, IS_DEL_EXECUTED), 0) = 1
          AND ISNULL(CASE WHEN ISNUMERIC(DC_RES) = 1 THEN CONVERT(INT, DC_RES) ELSE NULL END, 0) IN (1, 2)
          AND ISNULL(DC_RESDATE, ISNULL(DC_cmd_date, DC_DATE)) < DATEADD(DAY, -@RetentionDays, GETDATE());

        SET @deleted = @@ROWCOUNT;
        SET @totalDeleted = @totalDeleted + @deleted;
    END

    PRINT '[usp_devicecmd_retention_cleanup] deleted rows = ' + CAST(@totalDeleted AS VARCHAR(20));
END
GO

/* 4) Run once immediately */
EXEC dbo.usp_devicecmd_retention_cleanup
    @RetentionDays = 0,
    @BatchSize = 2000,
    @MaxLoops = 30;
GO

/*
  5) Create SQL Agent job (if Agent is available).
     Schedule: every 30 minutes.
*/
IF DB_ID('msdb') IS NOT NULL
BEGIN
    BEGIN TRY
        IF NOT EXISTS (
            SELECT 1
            FROM msdb.dbo.sysjobs
            WHERE name = N'DEVICECMD_RETENTION_CLEANUP'
        )
        BEGIN
            DECLARE @jobId UNIQUEIDENTIFIER;
            DECLARE @jobDatabase SYSNAME = DB_NAME();

            EXEC msdb.dbo.sp_add_job
                @job_name = N'DEVICECMD_RETENTION_CLEANUP',
                @enabled = 1,
                @description = N'Periodic cleanup of completed DEVICECMD rows.',
                @job_id = @jobId OUTPUT;

            EXEC msdb.dbo.sp_add_jobstep
                @job_id = @jobId,
                @step_name = N'Cleanup DEVICECMD completed rows',
                @subsystem = N'TSQL',
                @database_name = @jobDatabase,
                @command = N'EXEC dbo.usp_devicecmd_retention_cleanup @RetentionDays = 0, @BatchSize = 2000, @MaxLoops = 20;',
                @retry_attempts = 1,
                @retry_interval = 5;

            EXEC msdb.dbo.sp_add_schedule
                @schedule_name = N'DEVICECMD_RETENTION_EVERY_30_MIN',
                @enabled = 1,
                @freq_type = 4,
                @freq_interval = 1,
                @freq_subday_type = 4,
                @freq_subday_interval = 30,
                @active_start_time = 000000;

            EXEC msdb.dbo.sp_attach_schedule
                @job_id = @jobId,
                @schedule_name = N'DEVICECMD_RETENTION_EVERY_30_MIN';

            EXEC msdb.dbo.sp_add_jobserver
                @job_id = @jobId,
                @server_name = N'(LOCAL)';

            PRINT 'SQL Agent job DEVICECMD_RETENTION_CLEANUP created.';
        END
        ELSE
        BEGIN
            PRINT 'SQL Agent job DEVICECMD_RETENTION_CLEANUP already exists.';
        END
    END TRY
    BEGIN CATCH
        PRINT 'SQL Agent job setup skipped: ' + ERROR_MESSAGE();
    END CATCH
END
GO

PRINT 'Setup complete. DEVICECMD cleanup is now automated.';
