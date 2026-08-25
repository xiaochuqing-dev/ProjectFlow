param(
    [string]$Root,
    [switch]$Create,
    [switch]$Verify,
    [string]$ProductVersion = 'unknown',
    [string]$SourceSha = 'unknown',
    [string]$FlywaySchemaVersion = 'unknown',
    [string]$SpringBootVersion = 'unknown',
    [string]$BuildMode = 'windows-portable'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

if ($Create -and $Verify) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'Choose either -Create or -Verify.' }
if (-not $Root) { $Root = Get-ReleaseRoot -ScriptRoot $PSScriptRoot }
$Root = Get-AbsolutePath $Root
$manifestPath = Join-Path $Root 'manifest.json'
$checksumScript = Join-Path $PSScriptRoot 'checksum-projectflow.ps1'

function Get-ChecksumPayload {
    $checksumPath = Join-Path $Root 'checksums.sha256.json'
    if (-not (Test-Path -LiteralPath $checksumPath -PathType Leaf)) {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'Create checksums before creating the release manifest.'
    }
    return Get-Content -LiteralPath $checksumPath -Raw | ConvertFrom-Json
}

function Assert-RequiredReleaseArtifacts {
    $required = @(
        'backend/projectflow.jar',
        'frontend/server.js',
        'frontend/.next/static',
        'runtime/java/bin/java.exe',
        'runtime/node/node.exe',
        'runtime/h2/h2-restore.jar',
        'scripts/release/backup-projectflow.ps1',
        'scripts/release/checksum-projectflow.ps1',
        'scripts/release/manifest-projectflow.ps1',
        'scripts/release/start-projectflow.ps1',
        'scripts/release/stop-projectflow.ps1',
        'scripts/release/restore-projectflow.ps1',
        'scripts/release/dpapi-smoke.ps1'
    )
    foreach ($relative in $required) {
        $path = Join-Path $Root ($relative.Replace('/', '\'))
        if ($relative.EndsWith('/static')) {
            if (-not (Test-Path -LiteralPath $path -PathType Container)) {
                Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' "A required release directory is missing."
            }
        } elseif (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' "A required release artifact is missing."
        }
    }
}

if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release root does not exist.'
}

if ($Create) {
    Assert-RequiredReleaseArtifacts
    & $checksumScript -Root $Root -Create | Out-Null
    $checksum = Get-ChecksumPayload
    $nextPackage = Join-Path $Root 'frontend/node_modules/next/package.json'
    $nextVersion = 'unknown'
    if (Test-Path -LiteralPath $nextPackage -PathType Leaf) {
        try { $nextVersion = [string](Get-Content -LiteralPath $nextPackage -Raw | ConvertFrom-Json).version } catch { $nextVersion = 'unknown' }
    }
    $flywayProvenance = 'build-parameter'
    if ($FlywaySchemaVersion -eq 'unknown') { $flywayProvenance = 'not-determined-at-build' }
    $manifest = [ordered]@{
        schemaVersion = 'projectflow-release-manifest-v1'
        productVersion = $ProductVersion
        sourceSha = $SourceSha
        buildTimeUtc = (Get-Date).ToUniversalTime().ToString('o')
        buildMode = $BuildMode
        springBootVersion = $SpringBootVersion
        nextVersion = $nextVersion
        javaRuntimeVersion = Get-InstalledRuntimeVersion -Executable (Join-Path $Root 'runtime/java/bin/java.exe')
        nodeRuntimeVersion = Get-InstalledRuntimeVersion -Executable (Join-Path $Root 'runtime/node/node.exe')
        flywaySchemaVersion = $FlywaySchemaVersion
        flywaySchemaVersionProvenance = $flywayProvenance
        runtimeDataContractVersion = 'projectflow-runtime-data-v1'
        checksumFile = 'checksums.sha256.json'
        checksumFileSha256 = Get-FileSha256Hex -Path (Join-Path $Root 'checksums.sha256.json')
        archiveSha256 = $null
        archiveSha256Provenance = 'external-sidecar'
        files = @($checksum.files)
        requiredArtifacts = @(
            'backend/projectflow.jar',
            'frontend/server.js',
            'frontend/.next/static',
            'runtime/java/bin/java.exe',
            'runtime/node/node.exe',
            'runtime/h2/h2-restore.jar',
            'scripts/release/backup-projectflow.ps1',
            'scripts/release/checksum-projectflow.ps1',
            'scripts/release/manifest-projectflow.ps1',
            'scripts/release/start-projectflow.ps1',
            'scripts/release/stop-projectflow.ps1',
            'scripts/release/restore-projectflow.ps1',
            'scripts/release/dpapi-smoke.ps1'
        )
    }
    Write-AtomicText -Path $manifestPath -Content (($manifest | ConvertTo-Json -Depth 10) + "`n")
    Write-Output 'MANIFEST_CREATED'
    exit 0
}

if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'manifest.json is missing.'
}
& $checksumScript -Root $Root -Verify | Out-Null
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 'projectflow-release-manifest-v1') {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release manifest schema is unsupported.'
}
$checksum = Get-ChecksumPayload
if ([string]$manifest.checksumFile -ne 'checksums.sha256.json') {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release manifest checksum file name is unsupported.'
}
foreach ($property in @('productVersion','sourceSha','buildTimeUtc','buildMode','runtimeDataContractVersion','checksumFileSha256')) {
    if ($null -eq $manifest.$property -or [string]::IsNullOrWhiteSpace([string]$manifest.$property)) {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release manifest is incomplete.'
    }
}
if ($null -eq $manifest.files -or @($manifest.files).Count -eq 0) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release manifest has no payload file entries.'
}
if ([string]$manifest.sourceSha -notmatch '^[0-9a-fA-F]{40,64}$') {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release manifest source SHA is not a full revision.'
}
if ([string]$manifest.archiveSha256Provenance -ne 'external-sidecar' -or $null -ne $manifest.archiveSha256) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The archive hash provenance is not an external sidecar.'
}
if ($manifest.runtimeDataContractVersion -ne 'projectflow-runtime-data-v1') {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The runtime data contract is unsupported.'
}
if ((Get-FileSha256Hex -Path (Join-Path $Root 'checksums.sha256.json')) -ne [string]$manifest.checksumFileSha256) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The checksum manifest does not match manifest.json.'
}
$manifestEntries = @($manifest.files)
$checksumEntries = @($checksum.files)
$manifestPaths = @($manifestEntries | ForEach-Object { [string]$_.path } | Sort-Object)
$checksumPaths = @($checksumEntries | ForEach-Object { [string]$_.path } | Sort-Object)
if (($manifestPaths -join "`n") -ne ($checksumPaths -join "`n")) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release manifest payload does not match the checksum manifest.'
}
foreach ($entry in $manifestEntries) {
    $matching = @($checksumEntries | Where-Object { [string]$_.path -eq [string]$entry.path })
    if ($matching.Count -ne 1 -or [int64]$matching[0].bytes -ne [int64]$entry.bytes -or [string]$matching[0].sha256 -ne [string]$entry.sha256) {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release manifest payload metadata does not match its checksum entry.'
    }
}
Assert-RequiredReleaseArtifacts
$manifestText = Get-Content -LiteralPath $manifestPath -Raw
if ($manifestText -match '(?i)([A-Za-z]:[\\/]|\\\\|/Users/|/home/|/root/)') {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release manifest contains an absolute path.'
}
Write-Output 'MANIFEST_OK'
