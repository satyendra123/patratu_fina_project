param(
    [string]$Server = "localhost,1433",
    [string]$Database = "IDSL_NTPC_CLIMS",
    [string]$OutputRoot = "",
    [string[]]$EnrollIds
)

$ErrorActionPreference = "Stop"

function ConvertTo-SafeName {
    param([string]$Value, [int]$MaxLength = 40)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return "NA"
    }
    $safe = [regex]::Replace($Value.Trim(), "[^A-Za-z0-9._-]", "_")
    $safe = $safe.Trim("_")
    if ([string]::IsNullOrWhiteSpace($safe)) {
        $safe = "NA"
    }
    if ($safe.Length -gt $MaxLength) {
        $safe = $safe.Substring(0, $MaxLength)
    }
    return $safe
}

function ConvertFrom-DbImageText {
    param([string]$RawValue)

    if ([string]::IsNullOrWhiteSpace($RawValue)) {
        return $null
    }

    $value = $RawValue.Trim()
    if ($value -match "^data:image\/[^;]+;base64,") {
        $value = $value.Substring($value.IndexOf(",") + 1)
    }

    $value = $value.Replace("`r", "").Replace("`n", "").Trim()

    $attempts = New-Object System.Collections.Generic.List[string]
    $attempts.Add($value)

    if ($value.Contains("%")) {
        try {
            $attempts.Add([System.Uri]::UnescapeDataString($value))
        } catch {
            # Ignore decode failure and keep other attempts
        }
    }

    $attempts.Add($value.Replace("-", "+").Replace("_", "/"))

    foreach ($candidateRaw in ($attempts | Select-Object -Unique)) {
        if ([string]::IsNullOrWhiteSpace($candidateRaw)) {
            continue
        }

        $candidate = $candidateRaw
        $candidate = $candidate.Replace("%2B", "+").Replace("%2b", "+")
        $candidate = $candidate.Replace("%2F", "/").Replace("%2f", "/")
        $candidate = $candidate.Replace("%3D", "=").Replace("%3d", "=")
        $candidate = $candidate.Replace(" ", "+")
        $candidate = [regex]::Replace($candidate, "[^A-Za-z0-9\+/=]", "")

        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }

        $mod = $candidate.Length % 4
        if ($mod -ne 0) {
            $candidate = $candidate.PadRight($candidate.Length + (4 - $mod), "=")
        }

        try {
            return [System.Convert]::FromBase64String($candidate)
        } catch {
            # Try next variant
        }
    }

    return $null
}

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $PSScriptRoot "..\db-user-export\decoded-jpg"
}

$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
$mode = if ($EnrollIds -and $EnrollIds.Count -gt 0) { "selected" } else { "all" }
$runTag = "{0}_{1}" -f $mode, (Get-Date -Format "yyyyMMdd_HHmmss")
$runFolder = Join-Path $OutputRoot $runTag
New-Item -ItemType Directory -Path $runFolder -Force | Out-Null

$connectionString = "Server=$Server;Database=$Database;Integrated Security=true;Encrypt=true;TrustServerCertificate=true"
$idFilterSql = ""

if ($EnrollIds -and $EnrollIds.Count -gt 0) {
    $escapedIds = $EnrollIds | ForEach-Object { "'" + $_.Replace("'", "''") + "'" }
    $idFilterSql = " AND p.ID IN (" + ($escapedIds -join ",") + ")"
}

$sql = @"
;WITH latestPic AS (
    SELECT
        p.BP_ID,
        p.ID,
        p.IMAGE,
        p.UPD_DATE,
        p.BP_DATE,
        ROW_NUMBER() OVER (
            PARTITION BY p.ID
            ORDER BY ISNULL(p.UPD_DATE, p.BP_DATE) DESC, p.BP_ID DESC
        ) AS rn
    FROM BIO_PICDATA p
),
latestUser AS (
    SELECT
        u.ID,
        u.NAME,
        u.PRI,
        u.ISDELETED,
        ROW_NUMBER() OVER (
            PARTITION BY u.ID
            ORDER BY ISNULL(u.UPD_DATE, u.TR_DATE) DESC, u.BU_ID DESC
        ) AS rn
    FROM BIO_USERMAST u
)
SELECT
    p.ID AS enroll_id,
    u.NAME AS user_name,
    u.PRI AS privilege,
    ISNULL(u.ISDELETED, 0) AS is_deleted,
    CAST(p.IMAGE AS NVARCHAR(MAX)) AS image_data,
    p.BP_ID,
    ISNULL(p.UPD_DATE, p.BP_DATE) AS image_updated_at
FROM latestPic p
LEFT JOIN latestUser u
    ON u.ID = p.ID AND u.rn = 1
WHERE p.rn = 1$idFilterSql
ORDER BY CASE WHEN ISNUMERIC(p.ID) = 1 THEN CONVERT(BIGINT, p.ID) ELSE 9223372036854775807 END, p.ID;
"@

$conn = New-Object System.Data.SqlClient.SqlConnection($connectionString)
$conn.Open()

try {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $sql
    $cmd.CommandTimeout = 0
    $reader = $cmd.ExecuteReader()

    $results = New-Object System.Collections.Generic.List[Object]
    $total = 0
    $success = 0
    $failed = 0

    while ($reader.Read()) {
        $total++

        $enrollId = [string]$reader["enroll_id"]
        $userName = if ($reader.IsDBNull($reader.GetOrdinal("user_name"))) { "" } else { [string]$reader["user_name"] }
        $privilege = if ($reader.IsDBNull($reader.GetOrdinal("privilege"))) { "" } else { [string]$reader["privilege"] }
        $isDeleted = if ($reader.IsDBNull($reader.GetOrdinal("is_deleted"))) { 0 } else { [int]$reader["is_deleted"] }
        $status = if ($isDeleted -eq 1) { "Inactive" } else { "Active" }
        $bpId = if ($reader.IsDBNull($reader.GetOrdinal("BP_ID"))) { "" } else { [string]$reader["BP_ID"] }
        $imageUpdatedAt = if ($reader.IsDBNull($reader.GetOrdinal("image_updated_at"))) { "" } else { [string]$reader["image_updated_at"] }
        $imageText = if ($reader.IsDBNull($reader.GetOrdinal("image_data"))) { "" } else { [string]$reader["image_data"] }

        $bytes = ConvertFrom-DbImageText -RawValue $imageText
        if ($null -eq $bytes -or $bytes.Length -eq 0) {
            $failed++
            $results.Add([pscustomobject]@{
                enroll_id         = $enrollId
                user_name         = $userName
                privilege         = $privilege
                status            = $status
                bp_id             = $bpId
                image_updated_at  = $imageUpdatedAt
                file_path         = ""
                byte_size         = 0
                result            = "FAILED"
                error             = "Image decode failed"
            }) | Out-Null
            continue
        }

        $safeId = ConvertTo-SafeName -Value $enrollId -MaxLength 30
        $safeName = ConvertTo-SafeName -Value $userName -MaxLength 40
        $fileBase = "{0}_{1}" -f $safeId, $safeName
        $fileName = "$fileBase.jpg"
        $filePath = Join-Path $runFolder $fileName
        $suffix = 1
        while (Test-Path $filePath) {
            $fileName = "{0}_{1}.jpg" -f $fileBase, $suffix
            $filePath = Join-Path $runFolder $fileName
            $suffix++
        }

        try {
            [System.IO.File]::WriteAllBytes($filePath, $bytes)
            $success++
            $results.Add([pscustomobject]@{
                enroll_id         = $enrollId
                user_name         = $userName
                privilege         = $privilege
                status            = $status
                bp_id             = $bpId
                image_updated_at  = $imageUpdatedAt
                file_path         = $filePath
                byte_size         = $bytes.Length
                result            = "OK"
                error             = ""
            }) | Out-Null
        } catch {
            $failed++
            $results.Add([pscustomobject]@{
                enroll_id         = $enrollId
                user_name         = $userName
                privilege         = $privilege
                status            = $status
                bp_id             = $bpId
                image_updated_at  = $imageUpdatedAt
                file_path         = $filePath
                byte_size         = 0
                result            = "FAILED"
                error             = $_.Exception.Message
            }) | Out-Null
        }
    }

    $reader.Close()

    $metadataFile = Join-Path $runFolder "export_metadata.csv"
    $results | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $metadataFile

    $summary = [pscustomobject]@{
        mode              = $mode
        server            = $Server
        database          = $Database
        run_folder        = $runFolder
        metadata_file     = $metadataFile
        total_rows        = $total
        success_jpg       = $success
        failed_jpg        = $failed
        generated_at      = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    }

    $summaryFile = Join-Path $runFolder "export_summary.json"
    $summary | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path $summaryFile

    Write-Output ("run_folder=" + $runFolder)
    Write-Output ("metadata_file=" + $metadataFile)
    Write-Output ("summary_file=" + $summaryFile)
    Write-Output ("total_rows=" + $total)
    Write-Output ("success_jpg=" + $success)
    Write-Output ("failed_jpg=" + $failed)
} finally {
    if ($conn.State -ne [System.Data.ConnectionState]::Closed) {
        $conn.Close()
    }
}
