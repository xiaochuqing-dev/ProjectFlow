param(
    [string]$Root,
    [switch]$Create,
    [switch]$Verify
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

if ($Create -and $Verify) { Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'Choose either -Create or -Verify.' }
if (-not $Root) { $Root = Get-ReleaseRoot -ScriptRoot $PSScriptRoot }
$Root = Get-AbsolutePath $Root
$checksumPath = Join-Path $Root 'checksums.sha256.json'

function Get-ChecksumEntries {
    param([string]$Directory)
    $entries = @()
    foreach ($file in (Get-DirectoryFileList -Root $Directory)) {
        $relative = Get-RelativePath -Root $Directory -Path $file.FullName
        $entries += [ordered]@{
            path = $relative
            bytes = [int64]$file.Length
            sha256 = Get-FileSha256Hex -Path $file.FullName
        }
    }
    return @($entries)
}

function Assert-ChecksumEntries {
    param([string]$Directory, $Entries)
    $expected = @($Entries | ForEach-Object { [string]$_.path } | Sort-Object)
    $actual = @(Get-DirectoryFileList -Root $Directory | ForEach-Object { Get-RelativePath -Root $Directory -Path $_.FullName } | Sort-Object)
    if (($expected -join "`n") -ne ($actual -join "`n")) {
        Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release contains an unexpected or missing payload file.'
    }
    foreach ($entry in $Entries) {
        $path = Join-Path $Directory ([string]$entry.path.Replace('/', '\'))
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'A manifest payload file is missing.'
        }
        $actualLength = (Get-Item -LiteralPath $path -Force).Length
        if ([int64]$entry.bytes -ne [int64]$actualLength) {
            Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'A release payload size does not match its checksum manifest.'
        }
        if ([string]$entry.sha256 -ne (Get-FileSha256Hex -Path $path)) {
            Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'A release payload checksum does not match its checksum manifest.'
        }
    }
}

if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The release root does not exist.'
}

if ($Create) {
    $payload = [ordered]@{
        schemaVersion = 'projectflow-release-checksums-v1'
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        files = @(Get-ChecksumEntries -Directory $Root)
    }
    Write-AtomicText -Path $checksumPath -Content (($payload | ConvertTo-Json -Depth 8) + "`n")
    Write-Output 'CHECKSUMS_CREATED'
    exit 0
}

if (-not (Test-Path -LiteralPath $checksumPath -PathType Leaf)) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'checksums.sha256.json is missing.'
}
$checksum = Get-Content -LiteralPath $checksumPath -Raw | ConvertFrom-Json
if ($checksum.schemaVersion -ne 'projectflow-release-checksums-v1' -or $null -eq $checksum.files) {
    Throw-ReleaseError 'RELEASE_RUNTIME_INCOMPLETE' 'The checksum manifest schema is unsupported.'
}
Assert-ChecksumEntries -Directory $Root -Entries @($checksum.files)
Write-Output 'CHECKSUMS_OK'
