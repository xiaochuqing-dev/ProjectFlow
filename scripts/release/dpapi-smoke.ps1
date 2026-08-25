param(
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
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { Throw-ReleaseError 'SECRET_STORE_UNAVAILABLE' 'DPAPI smoke is Windows-only.' }

try { Add-Type -AssemblyName System.Security } catch { }
$credentialDirectory = Join-Path $DataRoot 'config\credentials'
$blobPath = Join-Path $credentialDirectory '.dpapi-smoke.bin'
$sentinel = [Text.Encoding]::UTF8.GetBytes('projectflow-dpapi-smoke-sentinel')
$protected = [System.Security.Cryptography.ProtectedData]::Protect($sentinel, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
$stream = [System.IO.File]::Open($blobPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
try {
    $stream.Write($protected, 0, $protected.Length)
    $stream.Flush($true)
} finally { $stream.Dispose() }
try {
    $roundTrip = [System.Security.Cryptography.ProtectedData]::Unprotect([System.IO.File]::ReadAllBytes($blobPath), $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
    if ([Convert]::ToBase64String($sentinel) -ne [Convert]::ToBase64String($roundTrip)) { Throw-ReleaseError 'SECRET_STORE_UNAVAILABLE' 'DPAPI round-trip did not preserve the test value.' }
    Write-Output 'DPAPI_SMOKE_OK'
} finally {
    Remove-Item -LiteralPath $blobPath -Force -ErrorAction SilentlyContinue
}
