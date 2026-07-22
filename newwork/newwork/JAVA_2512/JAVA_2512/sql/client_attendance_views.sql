USE [IDSL_NTPC_CLIMS];
GO

CREATE OR ALTER VIEW dbo.vw_AttendanceLogs
AS
SELECT
    s.SRAW_ID         AS AttendanceId,
    s.EMP_CODE        AS EmployeeCode,
    s.ATT_DATE        AS AttendanceDateTime,
    s.IN_OUT          AS Direction,
    s.CARDNO          AS CardNumber,
    s.AREA_ID         AS AreaId,
    n.SLNO            AS DeviceSerialNumber,
    n.DeviceName      AS DeviceName,
    n.GATE            AS GateDirection,
    n.NET_AREA        AS AreaDescription,
    s.downloaded_date AS DownloadedAt,
    s.SAPID           AS SapId,
    s.BODY_TEMP       AS BodyTemperature,
    s.IS_MASKED       AS IsMasked,
    s.IO_UPDATE       AS ProcessingStatus
FROM dbo.SRAW AS s
LEFT JOIN dbo.NetWork AS n
    ON n.ID = s.AREA_ID
   AND ISNULL(n.ISDELETED, 0) = 0;
GO

CREATE OR ALTER VIEW dbo.vw_AttendanceRawDeviceLogs
AS
SELECT
    a.ATT_ID       AS AttendanceId,
    a.SLNO         AS DeviceSerialNumber,
    a.USER_CODE    AS EmployeeCode,
    a.ATT_DATE     AS AttendanceDate,
    a.ATT_DATETIME AS AttendanceDateTime,
    a.INOUT_ID     AS InOutId,
    CASE
        WHEN a.INOUT_ID = '0' THEN 'IN'
        WHEN a.INOUT_ID = '1' THEN 'OUT'
        ELSE a.INOUT_ID
    END            AS Direction,
    a.ISFTP        AS IsFtpProcessed,
    a.cap_date     AS CapturedAt,
    a.COL2         AS VerifyMode,
    a.COL3         AS EventCode,
    a.COL4         AS ReservedValue,
    a.COL5         AS AdditionalValue,
    a.COL6         AS RawAdditionalData,
    a.COL7         AS AdditionalText1,
    a.COL8         AS AdditionalText2,
    a.COL9         AS AdditionalText3
FROM dbo.ATTLOG AS a;
GO
