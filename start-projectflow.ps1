param (
    [switch]$CheckOnly,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

Write-Host "ProjectFlow V3.3 - Docker/team mode"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"
$logDir = Join-Path $root "logs"

function Normalize-ProcessPath {
    $envVars = [Environment]::GetEnvironmentVariables("Process")
    if ($envVars.Contains("Path") -and $envVars.Contains("PATH")) {
        $pathValue = [string]$envVars["Path"]
        if ([string]::IsNullOrWhiteSpace($pathValue)) {
            $pathValue = [string]$envVars["PATH"]
        }
        [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
        [Environment]::SetEnvironmentVariable("Path", $pathValue, "Process")
    }
}

function Resolve-Tool {
    param (
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [string]$Fallback
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    if ($Fallback -and (Test-Path $Fallback)) {
        return $Fallback
    }

    throw "Required tool was not found: $Name"
}

function Quote-ForPowerShell {
    param (
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return "'" + $Value.Replace("'", "''") + "'"
}

function Invoke-Checked {
    param (
        [Parameter(Mandatory = $true)]
        [string]$Label,
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory
    )

    Write-Host $Label
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Assert-DockerRunning {
    param (
        [Parameter(Mandatory = $true)]
        [string]$DockerPath
    )

    function Get-DockerServerVersion {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $output = & $DockerPath "info" "--format" "{{.ServerVersion}}" 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }

        if ($exitCode -ne 0) {
            return $null
        }

        return $output | Where-Object { $_ -and ($_ -notmatch "^WARNING:") } | Select-Object -Last 1
    }

    Write-Host "Checking Docker Desktop..."
    $serverVersion = Get-DockerServerVersion
    if (-not $serverVersion) {
        $dockerDesktopPath = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
        if (Test-Path $dockerDesktopPath) {
            Write-Host "Docker Desktop is not running. Starting Docker Desktop..."
            Start-Process -FilePath $dockerDesktopPath | Out-Null
            $deadline = (Get-Date).AddSeconds(120)
            while ((Get-Date) -lt $deadline -and -not $serverVersion) {
                Start-Sleep -Seconds 5
                $serverVersion = Get-DockerServerVersion
            }
        }
    }

    if (-not $serverVersion) {
        throw "Docker Desktop is not running. Start Docker Desktop, wait until it is ready, then run this script again."
    }

    Write-Host "Docker server: $serverVersion"
}

function Wait-ForHttp {
    param (
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return $true
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    return $false
}

function Get-PortPids {
    param (
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $matches = netstat -ano | Select-String (":$Port\s")
    return $matches | ForEach-Object {
        $parts = ($_.Line -split "\s+") | Where-Object { $_ }
        if ($parts.Count -ge 5 -and $parts[3] -eq "LISTENING") {
            $parts[4]
        }
    } | Sort-Object -Unique
}

function Assert-PortFree {
    param (
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $pids = @(Get-PortPids -Port $Port)
    if ($pids.Count -gt 0) {
        throw "Port $Port is already in use by PID(s): $($pids -join ', '). Close the old ProjectFlow process and run this script again."
    }
}

function Start-ProjectJob {
    param (
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(Mandatory = $true)]
        [string]$LogPath
    )

    Start-Job -Name $Name -ArgumentList $WorkingDirectory, $Command, $LogPath -ScriptBlock {
        param ($WorkingDirectory, $Command, $LogPath)
        Set-Location $WorkingDirectory
        powershell.exe -NoProfile -ExecutionPolicy Bypass -Command $Command *>&1 | Tee-Object -FilePath $LogPath -Append
    }
}

function Show-JobOutput {
    param (
        [object[]]$Jobs
    )

    foreach ($job in $Jobs) {
        if ($job) {
            Receive-Job -Job $job -Keep | ForEach-Object { Write-Host "[$($job.Name)] $_" }
        }
    }
}

Normalize-ProcessPath

$mavenPath = Resolve-Tool -Name "mvn.cmd" -Fallback (Join-Path $env:USERPROFILE "Desktop\apache-maven-3.9.9\bin\mvn.cmd")
$npmPath = Resolve-Tool -Name "npm.cmd" -Fallback "C:\Program Files\nodejs\npm.cmd"
$dockerPath = Resolve-Tool -Name "docker.exe" -Fallback "C:\Program Files\Docker\Docker\resources\bin\docker.exe"

if ($CheckOnly) {
    Write-Host "Docker: $dockerPath"
    Write-Host "Maven: $mavenPath"
    Write-Host "npm: $npmPath"
    Write-Host "Startup checks passed."
    exit 0
}

$jobs = @()
$dockerStarted = $false

try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Set-Location $root

    Assert-PortFree -Port 3000
    Assert-PortFree -Port 8080

    Assert-DockerRunning -DockerPath $dockerPath
    Invoke-Checked -Label "Starting PostgreSQL and Redis..." -FilePath $dockerPath -Arguments @("compose", "up", "-d") -WorkingDirectory $root
    $dockerStarted = $true

    Invoke-Checked -Label "Building frontend..." -FilePath $npmPath -Arguments @("run", "build") -WorkingDirectory $frontendDir

    $backendLog = Join-Path $logDir "backend.log"
    $frontendLog = Join-Path $logDir "frontend.log"
    Remove-Item -Force -ErrorAction SilentlyContinue $backendLog, $frontendLog

    $frontendUrl = "http://127.0.0.1:3000/login"
    $backendHealthUrl = "http://127.0.0.1:8080/api/health"

    $backendCommand = "`$env:SPRING_PROFILES_ACTIVE='local'; `$env:FRONTEND_ORIGIN='http://127.0.0.1:3000'; & " + (Quote-ForPowerShell $mavenPath) + " spring-boot:run"
    $frontendCommand = "`$env:NEXT_PUBLIC_API_BASE_URL='http://127.0.0.1:8080/api'; & " + (Quote-ForPowerShell $npmPath) + " run start -- --hostname 127.0.0.1 --port 3000"

    Write-Host "Starting backend in this console..."
    $backendJob = Start-ProjectJob -Name "backend" -WorkingDirectory $backendDir -Command $backendCommand -LogPath $backendLog
    $jobs += $backendJob

    Write-Host "Starting frontend in this console..."
    $frontendJob = Start-ProjectJob -Name "frontend" -WorkingDirectory $frontendDir -Command $frontendCommand -LogPath $frontendLog
    $jobs += $frontendJob

    Write-Host "Waiting for backend and frontend..."
    $backendReady = Wait-ForHttp -Url $backendHealthUrl -TimeoutSeconds 90
    $frontendReady = Wait-ForHttp -Url $frontendUrl -TimeoutSeconds 60
    Show-JobOutput -Jobs $jobs

    if (-not $backendReady) {
        throw "Backend did not become ready. See logs\backend.log."
    }
    if (-not $frontendReady) {
        throw "Frontend did not become ready. See logs\frontend.log."
    }

    if (-not $NoBrowser) {
        Start-Process $frontendUrl
    }

    Write-Host ""
    Write-Host "ProjectFlow is running."
    Write-Host "Frontend: $frontendUrl"
    Write-Host "Backend:  $backendHealthUrl"
    Write-Host "Logs:     $logDir"
    Write-Host ""
    Write-Host "Press Enter in this window to stop ProjectFlow."
    [Console]::ReadLine() | Out-Null
} catch {
    Write-Host ""
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "No extra windows were started. Fix the issue above and run start-projectflow.bat again."
    exit 1
} finally {
    foreach ($job in $jobs) {
        if ($job) {
            Stop-Job -Job $job -ErrorAction SilentlyContinue
            Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
        }
    }

    if ($dockerStarted) {
        Write-Host "Stopping Docker services..."
        & $dockerPath compose stop
    }
}
