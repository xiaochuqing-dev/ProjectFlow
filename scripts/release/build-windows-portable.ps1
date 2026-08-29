param(
    [string]$SourceRoot,
    [string]$OutputRoot,
    [switch]$Clean,
    [switch]$SkipNpmCi,
    [switch]$SkipTests,
    [string]$FlywaySchemaVersion = 'unknown'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

if (-not $SourceRoot) { $SourceRoot = Get-ReleaseRoot -ScriptRoot $PSScriptRoot }
$SourceRoot = Get-AbsolutePath $SourceRoot

$packageJsonPath = Join-Path $SourceRoot 'frontend/package.json'
if (-not (Test-Path -LiteralPath $packageJsonPath -PathType Leaf)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'frontend/package.json is missing from the source tree.'
}
$package = Get-Content -LiteralPath $packageJsonPath -Raw | ConvertFrom-Json
$productVersion = [string]$package.version
if ([string]::IsNullOrWhiteSpace($productVersion)) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The frontend package has no product version.' }
$pomPath = Join-Path $SourceRoot 'backend/pom.xml'
$springBootVersion = 'unknown'
if (Test-Path -LiteralPath $pomPath -PathType Leaf) {
    try {
        $pom = [xml](Get-Content -LiteralPath $pomPath -Raw)
        $springBootVersion = [string]$pom.project.parent.version
        if ([string]::IsNullOrWhiteSpace($springBootVersion)) { $springBootVersion = 'unknown' }
    } catch { $springBootVersion = 'unknown' }
}
$migrationRoot = Join-Path $SourceRoot 'backend\src\main\resources\db\migration'
if ($FlywaySchemaVersion -eq 'unknown') {
    $versions = @(Get-ChildItem -LiteralPath $migrationRoot -Filter 'V*__*.sql' -File -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.Name -match '^V(?<version>[0-9]+(?:[._][0-9]+)*)__') {
            $versionText = $Matches.version.Replace('_','.')
            [pscustomobject]@{
                text = $versionText
                parsed = [version]$(if ($versionText.Contains('.')) { $versionText } else { "$versionText.0" })
            }
        }
    })
    if ($versions.Count -eq 0) {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'No versioned Flyway migration was found.'
    }
    $FlywaySchemaVersion = [string](($versions | Sort-Object parsed -Descending | Select-Object -First 1).text)
}
if ($springBootVersion -eq 'unknown') {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The Spring Boot version could not be read from backend/pom.xml.'
}
if (-not $OutputRoot) { $OutputRoot = Join-Path $SourceRoot ("artifacts\projectflow-v$productVersion-windows-portable") }
$OutputRoot = Get-AbsolutePath $OutputRoot

if ((Test-SamePath $OutputRoot $SourceRoot) -or (Test-PathWithin $SourceRoot $OutputRoot)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The output root cannot contain or replace the source tree.'
}
if (Test-ReparsePath -Path $OutputRoot) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The output root or an ancestor is a junction or symlink.'
}
$archivePath = "$OutputRoot.zip"
if ((Test-Path -LiteralPath $OutputRoot) -and -not $Clean) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The output root already exists; pass -Clean only for an explicit rebuild.'
}
if ($Clean) {
    if ((Test-SamePath $OutputRoot $SourceRoot) -or (Test-PathWithin $SourceRoot $OutputRoot)) {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'Refusing to clean a source or ancestor directory.'
    }
    Remove-Item -LiteralPath $OutputRoot -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $archivePath,"$archivePath.sha256" -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

function Resolve-Executable {
    param([string]$Name, [string]$Fallback)
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    if ($Fallback -and (Test-Path -LiteralPath $Fallback -PathType Leaf)) { return $Fallback }
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' "The build tool $Name is unavailable."
}

function Invoke-Checked {
    param([string]$Label, [string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory)
    Write-Host $Label
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' "$Label failed." }
    } finally { Pop-Location }
}

$npm = Resolve-Executable 'npm.cmd' 'C:\Program Files\nodejs\npm.cmd'
$maven = Resolve-Executable 'mvn.cmd' (Join-Path $env:USERPROFILE 'Desktop\apache-maven-3.9.9\bin\mvn.cmd')
$node = Resolve-Executable 'node.exe' 'C:\Program Files\nodejs\node.exe'
$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $javaCommand = Resolve-Executable 'java.exe' 'C:\Program Files\Eclipse Adoptium\jdk-17.0.0.0-hotspot\bin\java.exe'
    $javaHome = Split-Path -Parent (Split-Path -Parent $javaCommand)
}
$jlink = Join-Path $javaHome 'bin\jlink.exe'
if (-not (Test-Path -LiteralPath $jlink -PathType Leaf)) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'JDK jlink is unavailable.' }

$sourceRevision = [string]$env:GITHUB_SHA
$git = Get-Command 'git.exe' -ErrorAction SilentlyContinue
if ($git) {
    $headRevision = 'unknown'
    $dirty = @()
    try {
        $headRevision = ([string](& $git.Source -c "safe.directory=$SourceRoot" -C $SourceRoot rev-parse HEAD 2>$null)).Trim()
        $dirty = @(& $git.Source -c "safe.directory=$SourceRoot" -C $SourceRoot status --porcelain 2>$null)
    } catch { $headRevision = 'unknown' }
    if ($dirty.Count -gt 0) {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'Release artifacts require a clean source tree.'
    }
    if ([string]::IsNullOrWhiteSpace($sourceRevision)) {
        $sourceRevision = $headRevision
    } elseif (
        ($headRevision -notmatch '^[0-9a-fA-F]{40,64}$') -or
        (-not [string]::Equals($sourceRevision, $headRevision, [StringComparison]::OrdinalIgnoreCase))
    ) {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The requested source revision does not match the checked-out source.'
    }
}
if ($sourceRevision -notmatch '^[0-9a-fA-F]{40,64}$') {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'A full source revision is required for release provenance.'
}

$frontendRoot = Join-Path $SourceRoot 'frontend'
$backendRoot = Join-Path $SourceRoot 'backend'
$env:NEXT_PUBLIC_API_BASE_URL = 'http://127.0.0.1:8080/api'
$env:NEXT_PUBLIC_API_PORT = '8080'
if (-not $SkipNpmCi) { Invoke-Checked 'Installing locked frontend dependencies' $npm @('ci') $frontendRoot }
Invoke-Checked 'Building Next standalone frontend' $npm @('run','build') $frontendRoot
if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot '.next\standalone\server.js') -PathType Leaf)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'Next did not produce .next/standalone/server.js.'
}
if (-not $SkipTests) { Invoke-Checked 'Checking frontend types' $npm @('run','lint') $frontendRoot }

Invoke-Checked 'Building Spring Boot backend jar' $maven @('-B','-DskipTests','package') $backendRoot
$jar = Get-ChildItem -LiteralPath (Join-Path $backendRoot 'target') -Filter '*.jar' -File | Where-Object { $_.Name -notmatch '\.original$' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'Spring Boot executable jar was not produced.' }

$dependencyDirectory = Join-Path $backendRoot 'target\release-dependencies'
New-Item -ItemType Directory -Force -Path $dependencyDirectory | Out-Null
Invoke-Checked 'Collecting locked H2 restore runtime' $maven @('-B','dependency:copy-dependencies','-DincludeArtifactIds=h2','-DincludeScope=runtime',("-DoutputDirectory=$dependencyDirectory")) $backendRoot
$h2 = Get-ChildItem -LiteralPath $dependencyDirectory -Filter 'h2-*.jar' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $h2) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The H2 runtime jar was not collected.' }

function Copy-DirectoryChildren {
    param([string]$Source, [string]$Destination)
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    foreach ($child in (Get-ChildItem -LiteralPath $Source -Force)) {
        Copy-Item -LiteralPath $child.FullName -Destination (Join-Path $Destination $child.Name) -Recurse -Force
    }
}

$stageBackend = Join-Path $OutputRoot 'backend'
$stageFrontend = Join-Path $OutputRoot 'frontend'
$stageRuntimeJava = Join-Path $OutputRoot 'runtime\java'
$stageRuntimeNode = Join-Path $OutputRoot 'runtime\node'
$stageRuntimeH2 = Join-Path $OutputRoot 'runtime\h2'
$stageScripts = Join-Path $OutputRoot 'scripts\release'
New-Item -ItemType Directory -Force -Path $stageBackend,$stageFrontend,$stageRuntimeNode,$stageRuntimeH2,$stageScripts | Out-Null
Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $stageBackend 'projectflow.jar') -Force
Copy-Item -LiteralPath $h2.FullName -Destination (Join-Path $stageRuntimeH2 'h2-restore.jar') -Force
Copy-Item -LiteralPath $node -Destination (Join-Path $stageRuntimeNode 'node.exe') -Force

if (Test-Path -LiteralPath $stageRuntimeJava) { Remove-Item -LiteralPath $stageRuntimeJava -Recurse -Force }
& $jlink --add-modules ALL-MODULE-PATH --output $stageRuntimeJava --strip-debug --no-header-files --no-man-pages --compress=2
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $stageRuntimeJava 'bin\java.exe') -PathType Leaf)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'jlink did not produce the bundled Java runtime.'
}

$standalone = Join-Path $frontendRoot '.next\standalone'
Copy-DirectoryChildren -Source $standalone -Destination $stageFrontend
$staticSource = Join-Path $frontendRoot '.next\static'
if (-not (Test-Path -LiteralPath $staticSource -PathType Container)) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'Next static output is missing.' }
Copy-DirectoryChildren -Source $staticSource -Destination (Join-Path $stageFrontend '.next\static')
$publicSource = Join-Path $frontendRoot 'public'
if (Test-Path -LiteralPath $publicSource -PathType Container) { Copy-DirectoryChildren -Source $publicSource -Destination (Join-Path $stageFrontend 'public') }
Copy-Item -LiteralPath $packageJsonPath -Destination (Join-Path $stageFrontend 'package.json') -Force

foreach ($script in (Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'scripts\release') -Filter '*.ps1' -File)) {
    Copy-Item -LiteralPath $script.FullName -Destination (Join-Path $stageScripts $script.Name) -Force
}
foreach ($document in @('THIRD_PARTY_NOTICES.md','README.md')) {
    $documentPath = Join-Path $SourceRoot $document
    if (Test-Path -LiteralPath $documentPath -PathType Leaf) { Copy-Item -LiteralPath $documentPath -Destination (Join-Path $OutputRoot $document) -Force }
}

$manifestScript = Join-Path $stageScripts 'manifest-projectflow.ps1'
& $manifestScript -Root $OutputRoot -Create -ProductVersion $productVersion -SourceSha $sourceRevision -FlywaySchemaVersion $FlywaySchemaVersion -SpringBootVersion $springBootVersion | Out-Null
$builtManifest = Get-Content -LiteralPath (Join-Path $OutputRoot 'manifest.json') -Raw | ConvertFrom-Json
foreach ($provenanceField in @('springBootVersion','nextVersion','javaRuntimeVersion','nodeRuntimeVersion','flywaySchemaVersion')) {
    if ([string]::IsNullOrWhiteSpace([string]$builtManifest.$provenanceField) -or [string]$builtManifest.$provenanceField -eq 'unknown') {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' "The release manifest has no resolved $provenanceField provenance."
    }
}
& $manifestScript -Root $OutputRoot -Verify | Out-Null

$archivePath = "$OutputRoot.zip"
if (Test-Path -LiteralPath $archivePath) { Remove-Item -LiteralPath $archivePath -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($OutputRoot, $archivePath, [System.IO.Compression.CompressionLevel]::Optimal, $false)
$archiveHash = Get-FileSha256Hex -Path $archivePath
Write-AtomicText -Path "$archivePath.sha256" -Content "$archiveHash *$([System.IO.Path]::GetFileName($archivePath))`n"

Write-Output "PORTABLE_BUILD_OK version=$productVersion sourceSha=$sourceRevision"
Write-Output "PORTABLE_ARCHIVE_SHA256 $archiveHash"
