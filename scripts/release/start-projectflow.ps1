param(
    [string]$DataRoot,
    [switch]$Portable,
    [string]$PortableRoot,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 3000,
    [switch]$NoBrowser,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
$ReleaseRoot = Get-ReleaseRoot -ScriptRoot $PSScriptRoot
$manifestScript = Join-Path $PSScriptRoot 'manifest-projectflow.ps1'
$backupScript = Join-Path $PSScriptRoot 'backup-projectflow.ps1'
$stopScript = Join-Path $PSScriptRoot 'stop-projectflow.ps1'

& $manifestScript -Root $ReleaseRoot -Verify | Out-Null
if (-not $DataRoot) { $DataRoot = $env:PROJECTFLOW_DATA_DIR }
if (-not $DataRoot) { $DataRoot = Get-DefaultDataRoot -ReleaseRoot $ReleaseRoot -Portable:$Portable -PortableRoot $PortableRoot }
$DataRoot = Initialize-DataRoot -DataRoot $DataRoot -ReleaseRoot $ReleaseRoot
$layout = Get-DataLayout -DataRoot $DataRoot
$backendJar = Join-Path $ReleaseRoot 'backend\projectflow.jar'
$frontendServer = Join-Path $ReleaseRoot 'frontend\server.js'
$java = Join-Path $ReleaseRoot 'runtime\java\bin\java.exe'
$node = Join-Path $ReleaseRoot 'runtime\node\node.exe'
foreach ($required in @($backendJar,$frontendServer,$java,$node)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'A required runtime artifact is missing.' }
}
if ($BackendPort -lt 1024 -or $BackendPort -gt 65535 -or $FrontendPort -lt 1024 -or $FrontendPort -gt 65535 -or $BackendPort -eq $FrontendPort) {
    Throw-ReleaseError 'PORT_CONFLICT' 'The configured runtime ports are invalid.'
}

function Convert-ToProcessRecord {
    param([Parameter(Mandatory = $true)]$Snapshot, [Parameter(Mandatory = $true)][int]$Port, [Parameter(Mandatory = $true)][string]$Artifact)
    return [ordered]@{
        pid = [int]$Snapshot.pid
        startedAtUtc = [string]$Snapshot.startedAtUtc
        commandHash = [string]$Snapshot.commandHash
        port = $Port
        artifact = $Artifact
    }
}

function Restore-EnvironmentValue {
    param([string]$Name, [string]$Value)
    if ($null -eq $Value) { Remove-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue }
    else { Set-Item -LiteralPath "Env:$Name" -Value $Value }
}

function Remove-VerifiedProcess {
    param($Record)
    if ($Record -and (Test-RecordedProcess -Record $Record -CommandMarkers (Get-RecordedProcessMarkers -Record $Record))) {
        Stop-Process -Id ([int]$Record.pid) -Force -ErrorAction SilentlyContinue
    }
}

function Clear-VerifiedStaleH2Lock {
    param([string]$LockPath)
    if (-not (Test-Path -LiteralPath $LockPath -PathType Leaf)) { return }
    $content = Get-Content -LiteralPath $LockPath -Raw -ErrorAction Stop
    $match = [regex]::Match($content, '(?m)^server=([^\r\n]+)$')
    if (-not $match.Success) { Throw-ReleaseError 'LEGACY_DATA_CONFLICT' 'The embedded database lock cannot be identity-verified.' }
    $endpoint = $match.Groups[1].Value
    $colon = $endpoint.LastIndexOf(':')
    if ($colon -lt 1) { Throw-ReleaseError 'LEGACY_DATA_CONFLICT' 'The embedded database lock endpoint is invalid.' }
    $host = $endpoint.Substring(0, $colon)
    $port = 0
    if (-not [int]::TryParse($endpoint.Substring($colon + 1), [ref]$port) -or $port -le 0) {
        Throw-ReleaseError 'LEGACY_DATA_CONFLICT' 'The embedded database lock endpoint is invalid.'
    }
    $live = Test-NetConnection -ComputerName $host -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
    if ($live) { Throw-ReleaseError 'LEGACY_DATA_CONFLICT' 'The embedded database is still owned by a live H2 server.' }
    Remove-Item -LiteralPath $LockPath -Force
}

$statePath = Join-Path $layout.run 'instance.json'
if (Test-Path -LiteralPath $statePath -PathType Leaf) {
    try {
        $existing = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $existingRecords = @($existing.backend,$existing.frontend) | Where-Object { $_ }
        foreach ($record in $existingRecords) {
            if (Test-RecordedProcess -Record $record -CommandMarkers (Get-RecordedProcessMarkers -Record $record)) {
                Write-Output 'ALREADY_RUNNING'
                exit 0
            }
        }
    } catch {
        Throw-ReleaseError 'PORT_CONFLICT' 'The existing ProjectFlow instance state cannot be verified.'
    }
    if ((Get-PortListeners -Port $BackendPort).Count -gt 0 -or (Get-PortListeners -Port $FrontendPort).Count -gt 0) {
        Throw-ReleaseError 'PORT_CONFLICT' 'A runtime port is occupied by an unknown process.'
    }
    Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
}
if ((Get-PortListeners -Port $BackendPort).Count -gt 0 -or (Get-PortListeners -Port $FrontendPort).Count -gt 0) {
    Throw-ReleaseError 'PORT_CONFLICT' 'A runtime port is occupied by an unknown process.'
}
Clear-VerifiedStaleH2Lock -LockPath (Join-Path $layout.database 'projectflow.lock.db')

if ($CheckOnly) {
    Write-Output 'RELEASE_PREFLIGHT_OK'
    exit 0
}

if (Test-Path -LiteralPath (Join-Path $layout.database 'projectflow.mv.db') -PathType Leaf) {
    & $backupScript -DataRoot $DataRoot -ReleaseRoot $ReleaseRoot -Reason 'pre-start-upgrade' -RequireDatabase | Out-Null
}

$instanceId = [guid]::NewGuid().ToString('N')
$backendLog = Join-Path $layout.logs 'backend.log'
$backendErrorLog = Join-Path $layout.logs 'backend-error.log'
$frontendLog = Join-Path $layout.logs 'frontend.log'
$frontendErrorLog = Join-Path $layout.logs 'frontend-error.log'
$backendProcess = $null
$frontendProcess = $null
$backendRecord = $null
$frontendRecord = $null
$environmentNames = @('SPRING_PROFILES_ACTIVE','PROJECTFLOW_FLYWAY_ENABLED','PROJECTFLOW_DATA_DIR','PROJECTFLOW_STORAGE_DATA_DIR','PROJECTFLOW_BACKUP_DIR','PROJECTFLOW_MIGRATION_BACKUP_DIR','PROJECTFLOW_CONFIG_DIR','PROJECTFLOW_LOG_DIR','PROJECTFLOW_TEMP_DIR','PROJECTFLOW_CACHE_DIR','FRONTEND_ORIGIN','SERVER_ADDRESS','SERVER_PORT','HOSTNAME','PORT','NODE_ENV','NEXT_PUBLIC_API_BASE_URL','NEXT_PUBLIC_API_PORT','PROJECTFLOW_RUNTIME_MODE','PROJECTFLOW_INSTANCE_ID')
$previousEnvironment = @{}
foreach ($name in $environmentNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

try {
    $env:SPRING_PROFILES_ACTIVE = 'embedded,release'
    $env:PROJECTFLOW_FLYWAY_ENABLED = 'true'
    $env:PROJECTFLOW_DATA_DIR = $layout.database
    $env:PROJECTFLOW_STORAGE_DATA_DIR = $layout.storage
    $env:PROJECTFLOW_BACKUP_DIR = $layout.backups
    $env:PROJECTFLOW_MIGRATION_BACKUP_DIR = $layout.backups
    $env:PROJECTFLOW_CONFIG_DIR = $layout.config
    $env:PROJECTFLOW_LOG_DIR = $layout.logs
    $env:PROJECTFLOW_TEMP_DIR = $layout.temp
    $env:PROJECTFLOW_CACHE_DIR = $layout.cache
    $env:FRONTEND_ORIGIN = "http://127.0.0.1:$FrontendPort,http://localhost:$FrontendPort"
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = [string]$BackendPort
    $env:PROJECTFLOW_RUNTIME_MODE = 'local-release'
    $env:PROJECTFLOW_INSTANCE_ID = $instanceId
    $backendArguments = @(
        "-Dprojectflow.instance.id=$instanceId",
        '-Dserver.address=127.0.0.1',
        "-Dserver.port=$BackendPort",
        '-jar',
        'backend\projectflow.jar'
    )
    $backendProcess = Start-Process -FilePath $java -ArgumentList $backendArguments -WorkingDirectory $ReleaseRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $backendLog -RedirectStandardError $backendErrorLog
    Start-Sleep -Milliseconds 500
    $backendSnapshot = Get-ProcessSnapshot -ProcessId $backendProcess.Id
    if (-not $backendSnapshot) { Throw-ReleaseError 'PROVIDER_UNAVAILABLE' 'The backend process exited before readiness.' }
    $backendRecord = Convert-ToProcessRecord -Snapshot $backendSnapshot -Port $BackendPort -Artifact 'backend/projectflow.jar'
    Write-AtomicText -Path $statePath -Content (([ordered]@{
        schemaVersion = 'projectflow-instance-v1'
        instanceId = $instanceId
        mode = 'local-release'
        runtimeDataContractVersion = 'projectflow-runtime-data-v1'
        backendPort = $BackendPort
        frontendPort = $FrontendPort
        startedAtUtc = [DateTime]::UtcNow.ToString('o')
        backend = $backendRecord
        frontend = $null
    } | ConvertTo-Json -Depth 8) + "`n")
    if (-not (Wait-HttpReady -Url "http://127.0.0.1:$BackendPort/api/health" -TimeoutSeconds 120 -ExpectedStatusCodes @(200))) {
        Throw-ReleaseError 'PROVIDER_UNAVAILABLE' 'The backend did not become ready.'
    }

    $env:HOSTNAME = '127.0.0.1'
    $env:PORT = [string]$FrontendPort
    $env:NODE_ENV = 'production'
    $env:NEXT_PUBLIC_API_BASE_URL = "http://127.0.0.1:$BackendPort/api"
    $env:NEXT_PUBLIC_API_PORT = [string]$BackendPort
    $frontendProcess = Start-Process -FilePath $node -ArgumentList @('server.js') -WorkingDirectory (Join-Path $ReleaseRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput $frontendLog -RedirectStandardError $frontendErrorLog
    Start-Sleep -Milliseconds 500
    $frontendSnapshot = Get-ProcessSnapshot -ProcessId $frontendProcess.Id
    if (-not $frontendSnapshot) { Throw-ReleaseError 'PROVIDER_UNAVAILABLE' 'The frontend process exited before readiness.' }
    $frontendRecord = Convert-ToProcessRecord -Snapshot $frontendSnapshot -Port $FrontendPort -Artifact 'frontend/server.js'
    $state = [ordered]@{
        schemaVersion = 'projectflow-instance-v1'
        instanceId = $instanceId
        mode = 'local-release'
        runtimeDataContractVersion = 'projectflow-runtime-data-v1'
        backendPort = $BackendPort
        frontendPort = $FrontendPort
        startedAtUtc = [DateTime]::UtcNow.ToString('o')
        backend = $backendRecord
        frontend = $frontendRecord
    }
    Write-AtomicText -Path $statePath -Content (($state | ConvertTo-Json -Depth 8) + "`n")
    Write-AtomicText -Path (Join-Path $layout.run 'backend.pid') -Content ([string]$backendRecord.pid + "`n")
    Write-AtomicText -Path (Join-Path $layout.run 'frontend.pid') -Content ([string]$frontendRecord.pid + "`n")
    if (-not (Wait-HttpReady -Url "http://127.0.0.1:$FrontendPort/login" -TimeoutSeconds 90 -ExpectedStatusCodes @(200))) {
        Throw-ReleaseError 'PROVIDER_UNAVAILABLE' 'The frontend did not become ready.'
    }
    if (-not $NoBrowser) { Start-Process "http://127.0.0.1:$FrontendPort/login" | Out-Null }
    Write-Output 'START_COMPLETE'
} catch {
    if ($frontendRecord) { Remove-VerifiedProcess -Record $frontendRecord }
    elseif ($frontendProcess) { Stop-Process -Id $frontendProcess.Id -Force -ErrorAction SilentlyContinue }
    if ($backendRecord) { Remove-VerifiedProcess -Record $backendRecord }
    elseif ($backendProcess) { Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $statePath,(Join-Path $layout.run 'backend.pid'),(Join-Path $layout.run 'frontend.pid') -Force -ErrorAction SilentlyContinue
    throw
} finally {
    foreach ($name in $environmentNames) { Restore-EnvironmentValue -Name $name -Value $previousEnvironment[$name] }
}
