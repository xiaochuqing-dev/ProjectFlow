param (
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"

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

function Wait-ForHttp {
    param (
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [int]$TimeoutSeconds = 45
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

Normalize-ProcessPath

$mavenPath = Resolve-Tool -Name "mvn.cmd" -Fallback (Join-Path $env:USERPROFILE "Desktop\apache-maven-3.9.9\bin\mvn.cmd")
$npmPath = Resolve-Tool -Name "npm.cmd" -Fallback "C:\Program Files\nodejs\npm.cmd"
$dockerPath = Resolve-Tool -Name "docker.exe"

if ($CheckOnly) {
    Write-Host "Docker: $dockerPath"
    Write-Host "Maven: $mavenPath"
    Write-Host "npm: $npmPath"
    Write-Host "Startup checks passed."
    exit 0
}

Set-Location $root

Write-Host "Starting PostgreSQL and Redis with Docker Compose..."
& $dockerPath compose up -d

$backendCommand = "Set-Location " + (Quote-ForPowerShell $backendDir) + "; `$env:SPRING_PROFILES_ACTIVE='local'; & " + (Quote-ForPowerShell $mavenPath) + " spring-boot:run"
$frontendCommand = "Set-Location " + (Quote-ForPowerShell $frontendDir) + "; `$env:NEXT_PUBLIC_API_BASE_URL='http://localhost:8080/api'; & " + (Quote-ForPowerShell $npmPath) + " run dev -- --hostname 127.0.0.1 --port 3000"

Write-Host "Opening backend terminal..."
Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $backendCommand) -WorkingDirectory $backendDir

Write-Host "Opening frontend terminal..."
Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $frontendCommand) -WorkingDirectory $frontendDir

Write-Host "Waiting for frontend..."
$frontendReady = Wait-ForHttp -Url "http://localhost:3000/login" -TimeoutSeconds 60

if ($frontendReady) {
    Start-Process "http://localhost:3000/login"
    Write-Host "ProjectFlow is running at http://localhost:3000/login"
} else {
    Start-Process "http://localhost:3000/login"
    Write-Host "Frontend is still starting. Browser opened; refresh after both terminals are ready."
}
