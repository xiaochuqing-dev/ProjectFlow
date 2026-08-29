param(
    [Parameter(Mandatory = $true)][string]$BackupManifest,
    [string]$DataRoot,
    [string]$ReleaseRoot
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
if (-not $ReleaseRoot) { $ReleaseRoot = Get-ReleaseRoot -ScriptRoot $PSScriptRoot }
$ReleaseRoot = Get-AbsolutePath $ReleaseRoot
if (-not $DataRoot) { $DataRoot = $env:PROJECTFLOW_DATA_DIR }
if (-not $DataRoot) { $DataRoot = Get-DefaultDataRoot -ReleaseRoot $ReleaseRoot }
$DataRoot = Initialize-DataRoot -DataRoot $DataRoot -ReleaseRoot $ReleaseRoot
$layout = Get-DataLayout -DataRoot $DataRoot
$manifestPath = Get-AbsolutePath $BackupManifest
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { Throw-ReleaseError 'BACKUP_INVALID' 'The backup manifest is missing.' }
if (Test-ReparsePath -Path $manifestPath) { Throw-ReleaseError 'BACKUP_INVALID' 'The backup manifest path uses a junction or symlink.' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 'projectflow-backup-manifest-v1' -or $manifest.complete -ne $true) {
    Throw-ReleaseError 'BACKUP_INVALID' 'The backup manifest is incomplete or unsupported.'
}
foreach ($property in @('backupId','payloadBytes','sha256')) {
    if ($null -eq $manifest.$property -or [string]::IsNullOrWhiteSpace([string]$manifest.$property)) {
        Throw-ReleaseError 'BACKUP_INVALID' 'The backup manifest is missing required metadata.'
    }
}
$contractVersion = [string]$manifest.runtimeDataContractVersion
if ([string]::IsNullOrWhiteSpace($contractVersion)) { $contractVersion = 'projectflow-runtime-data-v1' }
if ($contractVersion -ne 'projectflow-runtime-data-v1' -and [string]$manifest.dataDirectoryContractVersion -ne 'v1') {
    Throw-ReleaseError 'BACKUP_INVALID' 'The backup data contract is unsupported.'
}
$payloadValue = [string]$manifest.payloadFile
if ([string]::IsNullOrWhiteSpace($payloadValue)) { $payloadValue = [string]$manifest.payload }
$payloadName = [System.IO.Path]::GetFileName($payloadValue)
if ($payloadName -ne $payloadValue) {
    Throw-ReleaseError 'BACKUP_INVALID' 'The backup payload must be a sibling file with a safe name.'
}
$payloadPath = Join-Path (Split-Path -Parent $manifestPath) $payloadName
if (-not (Test-Path -LiteralPath $payloadPath -PathType Leaf)) { Throw-ReleaseError 'BACKUP_INVALID' 'The backup payload is missing.' }
if (Test-ReparsePath -Path $payloadPath) { Throw-ReleaseError 'BACKUP_INVALID' 'The backup payload path uses a junction or symlink.' }
$payloadInfo = Get-Item -LiteralPath $payloadPath -Force
if ([int64]$manifest.payloadBytes -ne [int64]$payloadInfo.Length -or [string]$manifest.sha256 -ne (Get-FileSha256Hex -Path $payloadPath)) {
    Throw-ReleaseError 'BACKUP_INVALID' 'The backup payload checksum or size is invalid.'
}
$lockPath = Join-Path $layout.database 'projectflow.lock.db'
if (Test-Path -LiteralPath $lockPath -PathType Leaf) { Throw-ReleaseError 'RESTORE_BLOCKED' 'The embedded database lock is present; stop ProjectFlow first.' }
$statePath = Join-Path $layout.run 'instance.json'
if (Test-Path -LiteralPath $statePath -PathType Leaf) {
    try {
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        foreach ($record in @($state.backend,$state.frontend)) {
            if ($record -and (Test-RecordedProcess -Record $record -CommandMarkers (Get-RecordedProcessMarkers -Record $record))) { Throw-ReleaseError 'RESTORE_BLOCKED' 'ProjectFlow is still running.' }
        }
    } catch {
        if ($_.Exception.Message -match '^\[RESTORE_BLOCKED\]') { throw }
        Throw-ReleaseError 'RESTORE_BLOCKED' 'The ProjectFlow instance state cannot be verified.'
    }
}
$h2Jar = Join-Path $ReleaseRoot 'runtime\h2\h2-restore.jar'
$java = Join-Path $ReleaseRoot 'runtime\java\bin\java.exe'
if (-not (Test-Path -LiteralPath $h2Jar -PathType Leaf) -or -not (Test-Path -LiteralPath $java -PathType Leaf)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The bundled H2 restore runtime is missing.'
}

$restoreId = "restore-$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$restoreTemp = Join-Path $layout.temp $restoreId
$currentHold = Join-Path $layout.backups "$restoreId-current"
New-Item -ItemType Directory -Force -Path $restoreTemp | Out-Null
$currentMoved = @()
$installed = @()

try {
    $backupScript = Join-Path $PSScriptRoot 'backup-projectflow.ps1'
    if (Test-Path -LiteralPath (Join-Path $layout.database 'projectflow.mv.db') -PathType Leaf) {
        & $backupScript -DataRoot $DataRoot -ReleaseRoot $ReleaseRoot -Reason 'pre-restore-emergency' -RequireDatabase | Out-Null
    }
    & $java '-cp' $h2Jar 'org.h2.tools.Restore' '-file' $payloadPath '-dir' $restoreTemp '-db' 'projectflow'
    if ($LASTEXITCODE -ne 0) { Throw-ReleaseError 'RESTORE_VERIFICATION_FAILED' 'H2 rejected the backup payload.' }
    $restoredDb = Join-Path $restoreTemp 'projectflow.mv.db'
    if (-not (Test-Path -LiteralPath $restoredDb -PathType Leaf)) { Throw-ReleaseError 'RESTORE_VERIFICATION_FAILED' 'H2 restore did not produce a database file.' }
    $unexpectedRestored = @(Get-ChildItem -LiteralPath $restoreTemp -File -Force | Where-Object { $_.Name -notmatch '^projectflow\.[A-Za-z0-9._-]+$' })
    if ($unexpectedRestored.Count -gt 0) { Throw-ReleaseError 'RESTORE_VERIFICATION_FAILED' 'H2 restore produced an unexpected file.' }
    $testUrl = 'jdbc:h2:file:' + $restoreTemp.Replace('\', '/') + '/projectflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE'
    & $java '-cp' $h2Jar 'org.h2.tools.Shell' '-url' $testUrl '-user' 'sa' '-password' '' '-sql' 'SELECT 1' *> $null
    if ($LASTEXITCODE -ne 0) { Throw-ReleaseError 'RESTORE_VERIFICATION_FAILED' 'The restored H2 database could not be opened in isolation.' }

    $currentFiles = @(Get-ChildItem -LiteralPath $layout.database -Filter 'projectflow.*' -File -Force -ErrorAction SilentlyContinue)
    if ($currentFiles.Count -gt 0) {
        New-Item -ItemType Directory -Force -Path $currentHold | Out-Null
        foreach ($file in $currentFiles) {
            $destination = Join-Path $currentHold $file.Name
            Move-Item -LiteralPath $file.FullName -Destination $destination -Force
            $currentMoved += $destination
        }
    }
    $restoredFiles = @(Get-ChildItem -LiteralPath $restoreTemp -Filter 'projectflow.*' -File -Force)
    foreach ($file in $restoredFiles) {
        $destination = Join-Path $layout.database $file.Name
        Move-Item -LiteralPath $file.FullName -Destination $destination -Force
        $installed += $destination
    }
    # Re-open the installed copy before declaring success. Any failure enters
    # the rollback path below while the retained current files are intact.
    $finalUrl = 'jdbc:h2:file:' + $layout.database.Replace('\', '/') + '/projectflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE'
    & $java '-cp' $h2Jar 'org.h2.tools.Shell' '-url' $finalUrl '-user' 'sa' '-password' '' '-sql' 'SELECT 1' *> $null
    if ($LASTEXITCODE -ne 0) { Throw-ReleaseError 'RESTORE_VERIFICATION_FAILED' 'The installed H2 database could not be reopened.' }
    Write-AtomicText -Path (Join-Path $layout.run 'last-restore.json') -Content (([ordered]@{
        schemaVersion = 'projectflow-restore-record-v1'
        restoreId = $restoreId
        backupId = [string]$manifest.backupId
        completedAtUtc = [DateTime]::UtcNow.ToString('o')
        databaseType = 'H2'
        emergencyBackupCreated = $true
    } | ConvertTo-Json -Depth 5) + "`n")
    Write-Output 'RESTORE_COMPLETE'
} catch {
    foreach ($file in $installed) { Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue }
    foreach ($file in $currentMoved) {
        $destination = Join-Path $layout.database ([System.IO.Path]::GetFileName($file))
        if (Test-Path -LiteralPath $file -PathType Leaf) { Move-Item -LiteralPath $file -Destination $destination -Force }
    }
    throw
} finally {
    Remove-Item -LiteralPath $restoreTemp -Recurse -Force -ErrorAction SilentlyContinue
}
