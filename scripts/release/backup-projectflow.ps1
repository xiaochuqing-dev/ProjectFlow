param(
    [string]$DataRoot,
    [string]$ReleaseRoot,
    [string]$Reason = 'pre-upgrade',
    [switch]$RequireDatabase
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
if (-not $ReleaseRoot) { $ReleaseRoot = Get-ReleaseRoot -ScriptRoot $PSScriptRoot }
$ReleaseRoot = Get-AbsolutePath $ReleaseRoot
if (-not $DataRoot) { $DataRoot = $env:PROJECTFLOW_DATA_DIR }
if (-not $DataRoot) { $DataRoot = Get-DefaultDataRoot -ReleaseRoot $ReleaseRoot }
$DataRoot = Initialize-DataRoot -DataRoot $DataRoot -ReleaseRoot $ReleaseRoot
$layout = Get-DataLayout -DataRoot $DataRoot
$databaseFile = Join-Path $layout.database 'projectflow.mv.db'
$lockFile = Join-Path $layout.database 'projectflow.lock.db'
$h2Jar = Join-Path $ReleaseRoot 'runtime\h2\h2-restore.jar'

if (-not (Test-Path -LiteralPath $databaseFile -PathType Leaf)) {
    if ($RequireDatabase) { Throw-ReleaseError 'BACKUP_FAILED' 'The embedded database is missing.' }
    Write-Output 'NO_DATABASE'
    exit 0
}
if (Test-Path -LiteralPath $lockFile -PathType Leaf) {
    Throw-ReleaseError 'BACKUP_FAILED' 'The embedded database lock is present; stop ProjectFlow before backing up.'
}
if (-not (Test-Path -LiteralPath $h2Jar -PathType Leaf)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The bundled H2 restore runtime is missing.'
}
$safeReason = ([regex]::Replace([string]$Reason, '[^A-Za-z0-9_.-]', '-')).Trim('-')
if ([string]::IsNullOrWhiteSpace($safeReason)) { $safeReason = 'pre-upgrade' }
if ($safeReason.Length -gt 64) { $safeReason = $safeReason.Substring(0, 64) }
$backupId = "backup-$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$tempPayload = Join-Path $layout.temp "$backupId.zip.tmp"
$payloadName = "$backupId.zip"
$payloadPath = Join-Path $layout.backups $payloadName
$manifestName = "$backupId.manifest.json"
$manifestPath = Join-Path $layout.backups $manifestName
$databaseUrl = 'jdbc:h2:file:' + $layout.database.Replace('\', '/') + '/projectflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE'

try {
    $backupSql = "BACKUP TO '$($tempPayload.Replace('\\', '/'))'"
    & (Join-Path $ReleaseRoot 'runtime\java\bin\java.exe') '-cp' $h2Jar 'org.h2.tools.Shell' '-url' $databaseUrl '-user' 'sa' '-password' '' '-sql' $backupSql *> $null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $tempPayload -PathType Leaf)) {
        Throw-ReleaseError 'BACKUP_FAILED' 'H2 did not produce a backup payload.'
    }
    Move-Item -LiteralPath $tempPayload -Destination $payloadPath -Force
    $payloadInfo = Get-Item -LiteralPath $payloadPath -Force
    $manifest = [ordered]@{
        schemaVersion = 'projectflow-backup-manifest-v1'
        backupId = $backupId
        productVersion = 'unknown'
        sourceVersion = 'unknown'
        databaseType = 'h2'
        createdAt = [DateTime]::UtcNow.ToString('o')
        reason = $safeReason
        schemaClassification = 'unknown'
        flywayCurrentVersion = 'unknown'
        flywayTargetVersion = 'unknown'
        payloadFile = $payloadName
        payloadBytes = [int64]$payloadInfo.Length
        sha256 = Get-FileSha256Hex -Path $payloadPath
        complete = $true
        dataDirectoryContractVersion = 'v1'
        runtimeDataContractVersion = 'projectflow-runtime-data-v1'
        creationMethod = 'h2-jdbc-backup-sql'
    }
    $releaseManifest = Join-Path $ReleaseRoot 'manifest.json'
    if (Test-Path -LiteralPath $releaseManifest -PathType Leaf) {
        try {
            $releaseMetadata = Get-Content -LiteralPath $releaseManifest -Raw | ConvertFrom-Json
            $manifest.productVersion = [string]$releaseMetadata.productVersion
            $manifest.sourceVersion = [string]$releaseMetadata.sourceSha
        } catch { }
    }
    Write-AtomicText -Path $manifestPath -Content (($manifest | ConvertTo-Json -Depth 8) + "`n")
    Write-Output 'BACKUP_COMPLETE'
} catch {
    Remove-Item -LiteralPath $tempPayload -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $payloadPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $manifestPath -Force -ErrorAction SilentlyContinue
    throw
}
