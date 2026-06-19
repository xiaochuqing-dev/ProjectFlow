param (
    [string]$OutputDir
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataDir = Join-Path $root ".projectflow\local-data"

if (-not $OutputDir) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $root "artifacts\embedded-export\$timestamp"
}

if (-not (Test-Path $dataDir)) {
    throw "Embedded data directory does not exist: $dataDir"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
Copy-Item -Path (Join-Path $dataDir "*") -Destination $OutputDir -Recurse -Force

Write-Host "Embedded data exported."
Write-Host "Source: $dataDir"
Write-Host "Export: $OutputDir"
