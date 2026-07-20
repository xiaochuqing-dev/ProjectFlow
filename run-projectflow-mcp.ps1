$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "integrations\hermes\projectflow_mcp.py"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    [Console]::Error.WriteLine("ProjectFlow MCP adapter was not found in this repository.")
    exit 2
}

$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if ($null -ne $pythonCommand) {
    & $pythonCommand.Source $scriptPath
    exit $LASTEXITCODE
}

$pyCommand = Get-Command py -ErrorAction SilentlyContinue
if ($null -ne $pyCommand) {
    & $pyCommand.Source -3 $scriptPath
    exit $LASTEXITCODE
}

[Console]::Error.WriteLine("Python 3 is required to run the repository-local ProjectFlow MCP adapter.")
exit 2
