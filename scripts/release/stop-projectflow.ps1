param(
    [string]$DataRoot,
    [string]$ReleaseRoot,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 3000
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
if (-not $ReleaseRoot) { $ReleaseRoot = Get-ReleaseRoot -ScriptRoot $PSScriptRoot }
$ReleaseRoot = Get-AbsolutePath $ReleaseRoot
if (-not $DataRoot) { $DataRoot = $env:PROJECTFLOW_DATA_DIR }
if (-not $DataRoot) { $DataRoot = Get-DefaultDataRoot -ReleaseRoot $ReleaseRoot }
$DataRoot = Initialize-DataRoot -DataRoot $DataRoot -ReleaseRoot $ReleaseRoot
$layout = Get-DataLayout -DataRoot $DataRoot
$statePath = Join-Path $layout.run 'instance.json'
if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    Write-Output 'NO_INSTANCE'
    exit 0
}

$state = $null
try { $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json } catch { Throw-ReleaseError 'RESTORE_BLOCKED' 'The ProjectFlow instance state cannot be read.' }
$statePorts = @($state.backendPort, $state.frontendPort) | ForEach-Object {
    if ($null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_)) { [int]$_ }
}
if ($statePorts.Count -eq 2) {
    $BackendPort = $statePorts[0]
    $FrontendPort = $statePorts[1]
}
if ($BackendPort -lt 1024 -or $BackendPort -gt 65535 -or $FrontendPort -lt 1024 -or $FrontendPort -gt 65535 -or $BackendPort -eq $FrontendPort) {
    Throw-ReleaseError 'PORT_CONFLICT' 'The recorded runtime ports are invalid.'
}
$records = @($state.backend,$state.frontend) | Where-Object { $_ }
foreach ($record in $records) {
    $snapshot = Get-ProcessSnapshot -ProcessId ([int]$record.pid)
    if (-not $snapshot) { continue }
    $markers = Get-RecordedProcessMarkers -Record $record
    if (-not (Test-RecordedProcess -Record $record -CommandMarkers $markers)) {
        Throw-ReleaseError 'PORT_CONFLICT' 'A recorded PID no longer belongs to this ProjectFlow instance.'
    }
    Stop-Process -Id ([int]$record.pid) -ErrorAction SilentlyContinue
    if (-not (Wait-ProcessGone -ProcessId ([int]$record.pid) -TimeoutSeconds 15)) {
        if (Test-RecordedProcess -Record $record -CommandMarkers $markers) {
            Stop-Process -Id ([int]$record.pid) -Force -ErrorAction SilentlyContinue
        }
        if (-not (Wait-ProcessGone -ProcessId ([int]$record.pid) -TimeoutSeconds 10)) {
            Throw-ReleaseError 'PORT_CONFLICT' 'A ProjectFlow process did not stop.'
        }
    }
}

$remaining = @((Get-PortListeners -Port $BackendPort) + (Get-PortListeners -Port $FrontendPort) | Sort-Object -Unique)
if ($remaining.Count -gt 0) {
    Throw-ReleaseError 'PORT_CONFLICT' 'A listener remains on a ProjectFlow port; no unknown process was terminated.'
}
Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $layout.run 'backend.pid'),(Join-Path $layout.run 'frontend.pid') -Force -ErrorAction SilentlyContinue
Write-Output 'STOP_COMPLETE'
