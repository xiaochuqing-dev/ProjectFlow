param (
    [switch]$CheckOnly,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

Write-Host "ProjectFlow V3.3.3 - local embedded mode"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"
$logDir = Join-Path $root "logs"
$dataDir = Join-Path $root ".projectflow\local-data"

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

function Stop-ProjectFlowProcesses {
    param (
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $portPids = @(Get-PortPids -Port $Port)
    if ($portPids.Count -eq 0) {
        return
    }

    Write-Host "Port $Port is in use by PID(s): $($portPids -join ', '). Stopping previous ProjectFlow process..."

    foreach ($procId in $portPids) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
        if (-not $proc) { continue }

        $cmdLine = $proc.CommandLine
        $isProjectFlow = $cmdLine -and (
            ($cmdLine -match [regex]::Escape($frontendDir)) -or
            ($cmdLine -match [regex]::Escape($backendDir)) -or
            ($cmdLine -match "ProjectFlowApplication") -or
            ($cmdLine -match "spring-boot:run")
        )

        if (-not $isProjectFlow) {
            throw "Port $Port is used by a non-ProjectFlow process (PID $procId). Close it manually and run this script again."
        }

        # Stop the Maven parent (spring-boot:run) too, so it does not linger as an orphan.
        $toStop = @($procId)
        $current = $proc
        for ($i = 0; $i -lt 4; $i++) {
            if (-not $current.ParentProcessId) { break }
            $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$($current.ParentProcessId)" -ErrorAction SilentlyContinue
            if (-not $parent) { break }
            if ($parent.CommandLine -and $parent.CommandLine -match "spring-boot:run") {
                $toStop += $parent.ProcessId
            }
            $current = $parent
        }

        foreach ($id in ($toStop | Sort-Object -Unique)) {
            Write-Host "  Stopping PID $id..."
            Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
        }
    }

    $deadline = (Get-Date).AddSeconds(15)
    while ((Get-Date) -lt $deadline) {
        if (@(Get-PortPids -Port $Port).Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 500
    }

    throw "Port $Port is still in use after stopping previous ProjectFlow processes. Close it manually and run this script again."
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

if ($CheckOnly) {
    Write-Host "Maven: $mavenPath"
    Write-Host "npm: $npmPath"
    Write-Host "Data: $dataDir"
    Write-Host "Embedded startup checks passed."
    exit 0
}

$jobs = @()

try {
    New-Item -ItemType Directory -Force -Path $logDir, $dataDir | Out-Null
    Set-Location $root

    Stop-ProjectFlowProcesses -Port 3000
    Stop-ProjectFlowProcesses -Port 8080

    $env:NEXT_PUBLIC_API_BASE_URL = "http://127.0.0.1:8080/api"
    $env:NEXT_PUBLIC_API_PORT = "8080"
    Invoke-Checked -Label "Building frontend..." -FilePath $npmPath -Arguments @("run", "build") -WorkingDirectory $frontendDir

    $backendLog = Join-Path $logDir "backend-embedded.log"
    $frontendLog = Join-Path $logDir "frontend-embedded.log"
    Remove-Item -Force -ErrorAction SilentlyContinue $backendLog, $frontendLog

    $frontendUrl = "http://127.0.0.1:3000/login"
    $backendHealthUrl = "http://127.0.0.1:8080/api/health"

    $backendCommand = "`$env:SPRING_PROFILES_ACTIVE='embedded'; `$env:PROJECTFLOW_DATA_DIR=" + (Quote-ForPowerShell $dataDir) + "; `$env:FRONTEND_ORIGIN='http://127.0.0.1:3000,http://localhost:3000'; & " + (Quote-ForPowerShell $mavenPath) + " spring-boot:run"
    $frontendCommand = "`$env:NEXT_PUBLIC_API_BASE_URL='http://127.0.0.1:8080/api'; & " + (Quote-ForPowerShell $npmPath) + " run start -- --hostname 127.0.0.1 --port 3000"

    Write-Host "Starting embedded backend..."
    $backendJob = Start-ProjectJob -Name "backend-embedded" -WorkingDirectory $backendDir -Command $backendCommand -LogPath $backendLog
    $jobs += $backendJob

    Write-Host "Starting frontend..."
    $frontendJob = Start-ProjectJob -Name "frontend-embedded" -WorkingDirectory $frontendDir -Command $frontendCommand -LogPath $frontendLog
    $jobs += $frontendJob

    Write-Host "Waiting for backend and frontend..."
    $backendReady = Wait-ForHttp -Url $backendHealthUrl -TimeoutSeconds 60
    $frontendReady = Wait-ForHttp -Url $frontendUrl -TimeoutSeconds 60
    Show-JobOutput -Jobs $jobs

    if (-not $backendReady) {
        throw "Embedded backend did not become ready. See logs\backend-embedded.log."
    }
    if (-not $frontendReady) {
        throw "Frontend did not become ready. See logs\frontend-embedded.log."
    }

    if (-not $NoBrowser) {
        Start-Process $frontendUrl
    }

    Write-Host ""
    Write-Host "ProjectFlow V3.3.3 embedded mode is running."
    Write-Host "Frontend: $frontendUrl"
    Write-Host "Backend:  $backendHealthUrl"
    Write-Host "Data:     $dataDir"
    Write-Host "Logs:     $logDir"
    Write-Host ""
    Write-Host "Press Enter in this window to stop ProjectFlow."
    [Console]::ReadLine() | Out-Null
} catch {
    Write-Host ""
    Write-Host "Embedded startup failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Fix the issue above and run start-projectflow-embedded.bat again."
    exit 1
} finally {
    foreach ($job in $jobs) {
        if ($job) {
            Stop-Job -Job $job -ErrorAction SilentlyContinue
            Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
        }
    }
}
