Set-StrictMode -Version Latest

function Get-ReleaseRoot {
    param([string]$ScriptRoot = $PSScriptRoot)
    return (Resolve-Path -LiteralPath (Join-Path $ScriptRoot "..\..")).Path
}

function Throw-ReleaseError {
    param(
        [Parameter(Mandatory = $true)][string]$Code,
        [Parameter(Mandatory = $true)][string]$Message
    )
    throw "[$Code] $Message"
}

function Get-AbsolutePath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return [System.IO.Path]::GetFullPath($Path)
}

function Test-SamePath {
    param(
        [Parameter(Mandatory = $true)][string]$Left,
        [Parameter(Mandatory = $true)][string]$Right
    )
    return [string]::Equals(
        (Get-AbsolutePath $Left).TrimEnd('\'),
        (Get-AbsolutePath $Right).TrimEnd('\'),
        [System.StringComparison]::OrdinalIgnoreCase
    )
}

function Test-PathWithin {
    param(
        [Parameter(Mandatory = $true)][string]$Child,
        [Parameter(Mandatory = $true)][string]$Parent
    )
    $childPath = (Get-AbsolutePath $Child).TrimEnd('\') + '\'
    $parentPath = (Get-AbsolutePath $Parent).TrimEnd('\') + '\'
    return $childPath.StartsWith($parentPath, [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-ReparsePath {
    param([Parameter(Mandatory = $true)][string]$Path)
    $candidate = Get-AbsolutePath $Path
    while ($true) {
        if (Test-Path -LiteralPath $candidate) {
            $item = Get-Item -LiteralPath $candidate -Force -ErrorAction Stop
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                return $true
            }
            if (-not $item.PSIsContainer) {
                return $false
            }
        }
        $parent = Split-Path -Parent $candidate
        if ([string]::IsNullOrWhiteSpace($parent) -or (Test-SamePath $parent $candidate)) {
            return $false
        }
        $candidate = $parent
    }
}

function Assert-DataRoot {
    param(
        [Parameter(Mandatory = $true)][string]$DataRoot,
        [Parameter(Mandatory = $true)][string]$ReleaseRoot
    )
    if ([string]::IsNullOrWhiteSpace($DataRoot)) {
        Throw-ReleaseError "DATA_DIRECTORY_UNSUPPORTED" "A data root is required."
    }
    $resolved = Get-AbsolutePath $DataRoot
    $root = [System.IO.Path]::GetPathRoot($resolved)
    if ((Test-SamePath $resolved $root) -or (Test-SamePath $resolved $ReleaseRoot) -or
        (Test-PathWithin $resolved $ReleaseRoot) -or (Test-PathWithin $ReleaseRoot $resolved)) {
        Throw-ReleaseError "DATA_DIRECTORY_UNWRITABLE" "The data root must be separate from the release root."
    }
    if (Test-ReparsePath $resolved) {
        Throw-ReleaseError "DATA_DIRECTORY_UNSUPPORTED" "The data root or an ancestor is a junction or symlink."
    }
    return $resolved.TrimEnd('\')
}

function Initialize-DataRoot {
    param(
        [Parameter(Mandatory = $true)][string]$DataRoot,
        [Parameter(Mandatory = $true)][string]$ReleaseRoot
    )
    $resolved = Assert-DataRoot -DataRoot $DataRoot -ReleaseRoot $ReleaseRoot
    Assert-DataRootFreeSpace -DataRoot $resolved
    $directories = @(
        $resolved,
        (Join-Path $resolved "data"),
        (Join-Path $resolved "data\database"),
        (Join-Path $resolved "data\storage"),
        (Join-Path $resolved "backups"),
        (Join-Path $resolved "logs"),
        (Join-Path $resolved "cache"),
        (Join-Path $resolved "config"),
        (Join-Path $resolved "config\credentials"),
        (Join-Path $resolved "temp"),
        (Join-Path $resolved "run")
    )
    foreach ($directory in $directories) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
    $probeName = ".preflight-$([guid]::NewGuid().ToString('N')).tmp"
    $movedPath = Join-Path (Join-Path $resolved "temp") "$probeName.complete"
    try {
        Write-AtomicText -Path $movedPath -Content "projectflow-runtime-data-v1"
        Write-AtomicText -Path $movedPath -Content "projectflow-runtime-data-v1-replaced"
        Remove-Item -LiteralPath $movedPath -Force -ErrorAction SilentlyContinue
    } catch {
        Remove-Item -LiteralPath $movedPath -Force -ErrorAction SilentlyContinue
        Throw-ReleaseError "DATA_DIRECTORY_UNWRITABLE" "The data root failed the atomic write preflight."
    }
    return $resolved
}

function Write-AtomicText {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $temporary = Join-Path $directory ("." + [System.IO.Path]::GetFileName($Path) + "." + [guid]::NewGuid().ToString('N') + ".tmp")
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Content)
    $stream = [System.IO.File]::Open($temporary, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try {
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    } finally {
        $stream.Dispose()
    }
    $replacementBackup = $null
    try {
        if (Test-Path -LiteralPath $Path -PathType Leaf) {
            $replacementBackup = Join-Path $directory ("." + [System.IO.Path]::GetFileName($Path) + "." + [guid]::NewGuid().ToString('N') + ".replace")
            [System.IO.File]::Replace($temporary, $Path, $replacementBackup, $true)
            Remove-Item -LiteralPath $replacementBackup -Force -ErrorAction SilentlyContinue
        } else {
            [System.IO.File]::Move($temporary, $Path)
        }
    } catch {
        Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
        throw
    }
}

function Get-FileSha256Hex {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Throw-ReleaseError "RELEASE_RUNTIME_INCOMPLETE" "A required file is missing."
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-RelativePath {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path
    )
    $rootPath = (Get-AbsolutePath $Root).TrimEnd('\') + '\'
    $filePath = Get-AbsolutePath $Path
    if (-not $filePath.StartsWith($rootPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        Throw-ReleaseError "RELEASE_RUNTIME_INCOMPLETE" "A release file escaped the install root."
    }
    return $filePath.Substring($rootPath.Length).Replace('\', '/')
}

function Get-DirectoryFileList {
    param([Parameter(Mandatory = $true)][string]$Root)
    $manifestPath = Get-AbsolutePath (Join-Path $Root 'manifest.json')
    $checksumPath = Get-AbsolutePath (Join-Path $Root 'checksums.sha256.json')
    return @(Get-ChildItem -LiteralPath $Root -Recurse -File -Force | Where-Object {
        # Only the two package-root metadata files are outside the payload.
        # A nested file with one of these names is payload and must not bypass
        # the complete-tree checksum comparison.
        -not (Test-SamePath $_.FullName $manifestPath) -and
        -not (Test-SamePath $_.FullName $checksumPath)
    } | Sort-Object { Get-RelativePath -Root $Root -Path $_.FullName })
}

function Get-DataLayout {
    param([Parameter(Mandatory = $true)][string]$DataRoot)
    return [ordered]@{
        root = $DataRoot
        database = Join-Path $DataRoot "data\database"
        storage = Join-Path $DataRoot "data\storage"
        backups = Join-Path $DataRoot "backups"
        logs = Join-Path $DataRoot "logs"
        cache = Join-Path $DataRoot "cache"
        config = Join-Path $DataRoot "config"
        credentials = Join-Path $DataRoot "config\credentials"
        temp = Join-Path $DataRoot "temp"
        run = Join-Path $DataRoot "run"
    }
}

function Assert-DataRootFreeSpace {
    param([Parameter(Mandatory = $true)][string]$DataRoot)
    $resolved = Get-AbsolutePath $DataRoot
    $driveRoot = [System.IO.Path]::GetPathRoot($resolved)
    try { $drive = [System.IO.DriveInfo]::new($driveRoot) } catch {
        Throw-ReleaseError 'DATA_DIRECTORY_UNSUPPORTED' 'The data root volume cannot be inspected.'
    }
    if (-not $drive.IsReady) {
        Throw-ReleaseError 'DATA_DIRECTORY_UNSUPPORTED' 'The data root volume is not ready.'
    }
    $databasePath = Join-Path $resolved 'data\database\projectflow.mv.db'
    $databaseBytes = [int64]0
    if (Test-Path -LiteralPath $databasePath -PathType Leaf) {
        $databaseBytes = [int64](Get-Item -LiteralPath $databasePath -Force).Length
    }
    # Reserve a conservative startup/backup cushion. H2 backup and restore
    # can transiently require more than one database-sized copy.
    $minimumBytes = [int64](256MB)
    if ($databaseBytes -gt 0 -and $databaseBytes -le ([int64]::MaxValue / 2)) {
        $minimumBytes = [Math]::Max($minimumBytes, $databaseBytes * 2)
    }
    if ([int64]$drive.AvailableFreeSpace -lt $minimumBytes) {
        Throw-ReleaseError 'DATA_DIRECTORY_UNWRITABLE' 'The data root volume does not have enough free space for startup and backup.'
    }
}

function Get-DefaultDataRoot {
    param(
        [Parameter(Mandatory = $true)][string]$ReleaseRoot,
        [switch]$Portable,
        [string]$PortableRoot
    )
    if ($Portable) {
        if (-not [string]::IsNullOrWhiteSpace($PortableRoot)) {
            return $PortableRoot
        }
        $parent = Split-Path -Parent (Get-AbsolutePath $ReleaseRoot)
        return (Join-Path $parent "ProjectFlow-data")
    }
    $localAppData = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
    if ([string]::IsNullOrWhiteSpace($localAppData)) {
        Throw-ReleaseError "DATA_DIRECTORY_UNSUPPORTED" "Windows LocalAppData is unavailable."
    }
    return (Join-Path $localAppData "ProjectFlow")
}

function Get-CommandPathHash {
    param([string]$CommandLine)
    if ($null -eq $CommandLine) { $CommandLine = "" }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($CommandLine)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try { return ([System.BitConverter]::ToString($sha256.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha256.Dispose() }
}

function Get-ProcessSnapshot {
    param([Parameter(Mandatory = $true)][int]$ProcessId)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    $startTime = $null
    if ($process.CreationDate) {
        try { $startTime = [System.Management.ManagementDateTimeConverter]::ToDateTime($process.CreationDate).ToUniversalTime().ToString('o') } catch { $startTime = $null }
    }
    return [ordered]@{
        pid = [int]$process.ProcessId
        executable = [string]$process.ExecutablePath
        commandLine = [string]$process.CommandLine
        commandHash = Get-CommandPathHash ([string]$process.CommandLine)
        startedAtUtc = $startTime
    }
}

function Test-RecordedProcess {
    param(
        [Parameter(Mandatory = $true)]$Record,
        [string[]]$CommandMarkers = @()
    )
    $snapshot = Get-ProcessSnapshot -ProcessId ([int]$Record.pid)
    if (-not $snapshot) { return $false }
    if ($Record.startedAtUtc -and $snapshot.startedAtUtc -and $Record.startedAtUtc -ne $snapshot.startedAtUtc) { return $false }
    if ($Record.commandHash -and $Record.commandHash -ne $snapshot.commandHash) { return $false }
    foreach ($marker in $CommandMarkers) {
        if (-not [string]::IsNullOrWhiteSpace($marker) -and ([string]$snapshot.commandLine).IndexOf($marker, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) { return $false }
    }
    return $true
}

function Get-RecordedProcessMarkers {
    param([Parameter(Mandatory = $true)]$Record)
    $artifact = [string]$Record.artifact
    if ([string]::IsNullOrWhiteSpace($artifact)) { return @() }
    $normalized = $artifact.Replace('/', '\')
    $leaf = [System.IO.Path]::GetFileName($normalized)
    if ([string]::IsNullOrWhiteSpace($leaf)) { return @() }
    return @($leaf)
}

function Wait-ProcessGone {
    param([Parameter(Mandatory = $true)][int]$ProcessId, [int]$TimeoutSeconds = 20)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Get-ProcessSnapshot -ProcessId $ProcessId)) { return $true }
        Start-Sleep -Milliseconds 250
    }
    return $false
}

function Get-PortListeners {
    param([Parameter(Mandatory = $true)][int]$Port)
    $listeners = @()
    try {
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop | Select-Object -ExpandProperty OwningProcess -Unique)
    } catch {
        $netstat = @(netstat -ano -p tcp 2>$null | Select-String (":$Port\s"))
        $listeners = @($netstat | ForEach-Object {
            $parts = ($_.Line -split '\s+') | Where-Object { $_ }
            if ($parts.Count -ge 5 -and $parts[3] -eq 'LISTENING') { [int]$parts[4] }
        } | Sort-Object -Unique)
    }
    return @($listeners | Where-Object { $_ -and [int]$_ -gt 0 } | ForEach-Object { [int]$_ } | Sort-Object -Unique)
}

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$TimeoutSeconds = 90,
        [int[]]$ExpectedStatusCodes = @(200)
    )
    if ($null -eq $ExpectedStatusCodes -or $ExpectedStatusCodes.Count -eq 0) {
        Throw-ReleaseError 'PROVIDER_UNAVAILABLE' 'At least one expected readiness status is required.'
    }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($ExpectedStatusCodes -contains [int]$response.StatusCode) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Get-InstalledRuntimeVersion {
    param([Parameter(Mandatory = $true)][string]$Executable, [string[]]$Arguments = @('--version'))
    try {
        $output = & $Executable @Arguments 2>$null
        if ($LASTEXITCODE -eq 0 -and $output) { return ([string]($output | Select-Object -First 1)).Trim() }
    } catch { }
    return 'unknown'
}
