/*
  Purpose:
    Show the device authentication method directly in ATTLOG so operators can
    distinguish card punches from face punches while reviewing attendance.

  Confirmed from IDSL_NTPC_CLIMS data on 2026-07-03:
    COL2 = 3 -> CARDDATA
    COL2 = 8 -> FACEDATA

  AUTH_TYPE is computed from COL2, so it also works for historical rows and
  does not require any change to the attendance insert process.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF COL_LENGTH('dbo.ATTLOG', 'AUTH_TYPE') IS NULL
BEGIN
    ALTER TABLE dbo.ATTLOG
    ADD AUTH_TYPE AS
    (
        CASE
            WHEN LTRIM(RTRIM(COL2)) = '3' THEN CONVERT(varchar(10), 'CARDDATA')
            WHEN LTRIM(RTRIM(COL2)) = '8' THEN CONVERT(varchar(10), 'FACEDATA')
            ELSE CONVERT(varchar(10), 'OTHER')
        END
    ) PERSISTED;
END;

GO

SELECT
    ATT_ID,
    USER_CODE,
    ATT_DATETIME,
    COL2 AS DEVICE_MODE,
    AUTH_TYPE
FROM dbo.ATTLOG
ORDER BY ATT_ID DESC;
