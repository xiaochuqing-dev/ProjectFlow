$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "integrations\obsidian\projectflow_obsidian.py"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    [Console]::Error.WriteLine("ProjectFlow Obsidian projection CLI was not found in this repository.")
    exit 2
}

$forwardedArgs = @($args)
$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if ($null -ne $pythonCommand) {
    & $pythonCommand.Source $scriptPath @forwardedArgs
    exit $LASTEXITCODE
}

$pyCommand = Get-Command py -ErrorAction SilentlyContinue
if ($null -ne $pyCommand) {
    & $pyCommand.Source -3 $scriptPath @forwardedArgs
    exit $LASTEXITCODE
}

[Console]::Error.WriteLine("Python 3 is required to run the repository-local ProjectFlow Obsidian projection CLI.")
exit 2
