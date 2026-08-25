$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

function Assert-ReleaseTest {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "RELEASE_SCRIPT_TEST_FAILED: $Message" }
}

$sourceRoot = Get-ReleaseRoot -ScriptRoot $PSScriptRoot
$nextConfig = Get-Content -LiteralPath (Join-Path $sourceRoot 'frontend\next.config.ts') -Raw
Assert-ReleaseTest ($nextConfig -match 'output:\s*["'']standalone["'']') 'Next standalone output is not configured.'
$startText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'start-projectflow.ps1') -Raw
Assert-ReleaseTest ($startText -match 'Start-Process[\s\S]*-WindowStyle Hidden') 'The runtime launcher does not hide background processes.'
Assert-ReleaseTest ($startText -notmatch '(?i)\bmvn(?:\.cmd)?\b|\bnpm(?:\.cmd)?\b|\bgit(?:\.exe)?\b') 'The runtime launcher invokes a developer build tool.'
Assert-ReleaseTest ($startText -match "embedded,release") 'The runtime launcher does not activate the release profile.'
Assert-ReleaseTest ($startText -match 'PROJECTFLOW_(BACKUP|CONFIG|LOG|TEMP|CACHE)_DIR') 'The runtime launcher does not inject the external runtime layout.'
Assert-ReleaseTest ($startText -match "'backend\\projectflow\.jar'") 'The backend artifact is not passed as a working-directory-relative path.'
Assert-ReleaseTest ($startText -match "ArgumentList @\('server\.js'\)") 'The frontend artifact is not passed as a working-directory-relative path.'
Assert-ReleaseTest ($startText -match 'ExpectedStatusCodes') 'The runtime launcher does not require an exact readiness status.'
$commonText = Get-Content -LiteralPath (Join-Path $PSScriptRoot '_common.ps1') -Raw
Assert-ReleaseTest ($commonText -match 'Get-RecordedProcessMarkers') 'The runtime process identity has no artifact marker check.'
Assert-ReleaseTest ($commonText -match 'AvailableFreeSpace') 'The runtime data root has no free-space preflight.'

$fixture = Join-Path ([IO.Path]::GetTempPath()) ("projectflow-release-script-test-" + [guid]::NewGuid().ToString('N'))
try {
    $directories = @(
        'backend', 'frontend', 'frontend\.next\static', 'runtime\java\bin', 'runtime\node', 'runtime\h2', 'scripts\release'
    )
    foreach ($directory in $directories) { New-Item -ItemType Directory -Force -Path (Join-Path $fixture $directory) | Out-Null }
    Set-Content -LiteralPath (Join-Path $fixture 'backend\projectflow.jar') -Value 'fixture' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'frontend\server.js') -Value 'fixture' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'frontend\package.json') -Value '{"dependencies":{"next":"16.2.11"}}' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $fixture 'runtime\java\bin\java.exe') -Value 'fixture' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'runtime\node\node.exe') -Value 'fixture' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'runtime\h2\h2-restore.jar') -Value 'fixture' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'scripts\release\start-projectflow.ps1') -Value 'Start-Process -WindowStyle Hidden' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'scripts\release\stop-projectflow.ps1') -Value 'stop' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'scripts\release\restore-projectflow.ps1') -Value 'restore' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'scripts\release\backup-projectflow.ps1') -Value 'backup' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixture 'scripts\release\dpapi-smoke.ps1') -Value 'dpapi' -Encoding ASCII
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '_common.ps1') -Destination (Join-Path $fixture 'scripts\release\_common.ps1') -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'checksum-projectflow.ps1') -Destination (Join-Path $fixture 'scripts\release\checksum-projectflow.ps1') -Force
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'manifest-projectflow.ps1') -Destination (Join-Path $fixture 'scripts\release\manifest-projectflow.ps1') -Force
    & (Join-Path $fixture 'scripts\release\manifest-projectflow.ps1') -Root $fixture -Create -ProductVersion '3.10.0' -SourceSha (('0' * 40) -join '') | Out-Null
    & (Join-Path $fixture 'scripts\release\manifest-projectflow.ps1') -Root $fixture -Verify | Out-Null
    Set-Content -LiteralPath (Join-Path $fixture 'backend\projectflow.jar') -Value 'tampered' -Encoding ASCII
    $failed = $false
    try { & (Join-Path $fixture 'scripts\release\manifest-projectflow.ps1') -Root $fixture -Verify | Out-Null } catch { $failed = $true }
    Assert-ReleaseTest $failed 'Checksum verification did not reject a changed payload.'
    Set-Content -LiteralPath (Join-Path $fixture 'backend\projectflow.jar') -Value 'fixture' -Encoding ASCII
    & (Join-Path $fixture 'scripts\release\manifest-projectflow.ps1') -Root $fixture -Create -ProductVersion '3.10.0' -SourceSha (('0' * 40) -join '') | Out-Null
    & (Join-Path $fixture 'scripts\release\manifest-projectflow.ps1') -Root $fixture -Verify | Out-Null
    $nestedMetadata = Join-Path $fixture 'frontend\nested\manifest.json'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $nestedMetadata) | Out-Null
    Set-Content -LiteralPath $nestedMetadata -Value '{"unexpected":true}' -Encoding UTF8
    $nestedFailed = $false
    try { & (Join-Path $fixture 'scripts\release\manifest-projectflow.ps1') -Root $fixture -Verify | Out-Null } catch { $nestedFailed = $true }
    Assert-ReleaseTest $nestedFailed 'Checksum verification ignored a nested manifest payload file.'
    Remove-Item -LiteralPath (Join-Path $fixture 'frontend\nested') -Recurse -Force
    $nestedData = Join-Path $fixture 'data'
    $guardFailed = $false
    try { Initialize-DataRoot -DataRoot $nestedData -ReleaseRoot $fixture | Out-Null } catch { $guardFailed = $true }
    Assert-ReleaseTest $guardFailed 'Data-root guard accepted a directory inside the install root.'
    Write-Output 'RELEASE_SCRIPT_TESTS_OK'
} finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}
