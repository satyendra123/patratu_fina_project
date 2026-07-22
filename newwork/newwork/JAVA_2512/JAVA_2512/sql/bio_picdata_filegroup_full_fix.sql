/*
Run this in SSMS against the same database seen in the error:
  BIO_DATA_NTPC_CLIMS

Error reference:
  SQLServer error 1105
  "Could not allocate space for object 'dbo.BIO_PICDATA'.'BIOPICIX1'
   because the 'PRIMARY' filegroup is full."
*/

SET NOCOUNT ON;

PRINT 'Database context: ' + DB_NAME();
PRINT 'Time: ' + CONVERT(VARCHAR(19), GETDATE(), 120);

/* 1) Quick file + filegroup visibility */
SELECT
    DB_NAME() AS database_name,
    df.name AS logical_file_name,
    df.type_desc,
    CAST(df.size / 128.0 AS DECIMAL(18,2)) AS size_mb,
    CASE
        WHEN df.max_size = -1 THEN 'UNLIMITED'
        ELSE CAST(CAST(df.max_size / 128.0 AS DECIMAL(18,2)) AS VARCHAR(40))
    END AS max_size_mb,
    CASE
        WHEN df.is_percent_growth = 1 THEN CAST(df.growth AS VARCHAR(40)) + ' %'
        ELSE CAST(CAST(df.growth / 128.0 AS DECIMAL(18,2)) AS VARCHAR(40)) + ' MB'
    END AS growth,
    fg.name AS filegroup_name,
    df.physical_name
FROM sys.database_files df
LEFT JOIN sys.filegroups fg
    ON df.data_space_id = fg.data_space_id
ORDER BY df.type_desc, df.file_id;

SELECT
    fg.name AS filegroup_name,
    CAST(SUM(df.size) / 128.0 AS DECIMAL(18,2)) AS allocated_mb,
    CAST(SUM(FILEPROPERTY(df.name, 'SpaceUsed')) / 128.0 AS DECIMAL(18,2)) AS used_mb,
    CAST((SUM(df.size) - SUM(FILEPROPERTY(df.name, 'SpaceUsed'))) / 128.0 AS DECIMAL(18,2)) AS free_mb
FROM sys.filegroups fg
JOIN sys.database_files df
    ON df.data_space_id = fg.data_space_id
GROUP BY fg.name
ORDER BY fg.name;

/* 2) BIO_PICDATA health checks */
EXEC sp_spaceused 'dbo.BIO_PICDATA';

SELECT
    i.name AS index_name,
    i.index_id,
    i.type_desc,
    p.rows AS row_count
FROM sys.indexes i
JOIN sys.partitions p
    ON p.object_id = i.object_id
   AND p.index_id = i.index_id
WHERE i.object_id = OBJECT_ID('dbo.BIO_PICDATA')
ORDER BY i.index_id;

SELECT
    COUNT(*) AS total_rows,
    COUNT(DISTINCT TRY_CONVERT(BIGINT, ID)) AS distinct_numeric_ids
FROM dbo.BIO_PICDATA;

SELECT TOP (20)
    ID,
    COUNT(*) AS row_count_per_id
FROM dbo.BIO_PICDATA
GROUP BY ID
HAVING COUNT(*) > 1
ORDER BY COUNT(*) DESC, ID;

/* 3) Ensure PRIMARY data files can grow (safe) */
DECLARE @growSql NVARCHAR(MAX) = N'';
SELECT @growSql = @growSql +
    N'ALTER DATABASE [' + DB_NAME() + N'] MODIFY FILE (NAME = N''' + df.name + N''', FILEGROWTH = 512MB, MAXSIZE = UNLIMITED);' + CHAR(10)
FROM sys.database_files df
WHERE df.type_desc = 'ROWS'
  AND df.data_space_id IN (
      SELECT data_space_id FROM sys.filegroups WHERE name = 'PRIMARY'
  );

PRINT 'Applying autogrowth settings to PRIMARY row files:';
PRINT @growSql;
EXEC sp_executesql @growSql;

/* 4) Optional: add one more data file to PRIMARY if disk has space
   - Change @newFilePath to a valid SQL Server data folder on your server.
   - Keep this block commented until the path is updated.
*/
/*
DECLARE @newFilePath NVARCHAR(4000) = N'D:\MSSQL\Data\BIO_DATA_NTPC_CLIMS_PRIMARY_02.ndf';
IF NOT EXISTS (
    SELECT 1 FROM sys.database_files WHERE physical_name = @newFilePath
)
BEGIN
    DECLARE @addFileSql NVARCHAR(MAX) =
        N'ALTER DATABASE [' + DB_NAME() + N'] ADD FILE (' +
        N'NAME = N''' + REPLACE(DB_NAME(), ']', '') + N'_PRIMARY_02'',' +
        N'FILENAME = N''' + REPLACE(@newFilePath, '''', '''''') + N''',' +
        N'SIZE = 1024MB, FILEGROWTH = 512MB, MAXSIZE = UNLIMITED) TO FILEGROUP [PRIMARY];';
    PRINT @addFileSql;
    EXEC sp_executesql @addFileSql;
END
*/

/* 5) Optional: one-time duplicate cleanup (keeps latest BP_ID per numeric ID)
   - Uncomment only after backup confirmation.
*/
/*
;WITH dedupe AS (
    SELECT
        BP_ID,
        ROW_NUMBER() OVER (
            PARTITION BY TRY_CONVERT(BIGINT, ID)
            ORDER BY BP_ID DESC
        ) AS rn
    FROM dbo.BIO_PICDATA
    WHERE TRY_CONVERT(BIGINT, ID) IS NOT NULL
)
DELETE FROM dedupe
WHERE rn > 1;
*/

/* 6) Optional: rebuild the affected index after space issue is fixed */
/*
ALTER INDEX [BIOPICIX1] ON dbo.BIO_PICDATA REBUILD;
*/

PRINT 'Done. Re-run the visibility queries above and verify PRIMARY free_mb increased.';
