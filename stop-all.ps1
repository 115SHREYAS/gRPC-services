$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$services = @(
    @{ Name = "pricing-service" },
    @{ Name = "inventory-service" },
    @{ Name = "analytics-service" },
    @{ Name = "order-service" },
    @{ Name = "api-gateway" }
)

$repoPattern = [Regex]::Escape($repoRoot)
$javaProcesses = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine
}

$found = @()
$stopped = @()
$notRunning = @()

foreach ($service in $services) {
    $name = $service.Name
    $servicePattern = [Regex]::Escape($name)
    $serviceRegex = "(?i)$repoPattern[\\\\/]+$servicePattern([\\\\/]|\\.jar|\\s|$)"
    $matches = $javaProcesses | Where-Object {
        $_.CommandLine -match $serviceRegex
    }

    if (-not $matches) {
        Write-Host ("Not running: {0}" -f $name) -ForegroundColor Yellow
        $notRunning += $name
        continue
    }

    $pids = $matches | Select-Object -ExpandProperty ProcessId
    Write-Host ("Found {0} (PID: {1})" -f $name, ($pids -join ", ")) -ForegroundColor Cyan
    $found += $name

    $failed = $false
    foreach ($pid in $pids) {
        try {
            Stop-Process -Id $pid -ErrorAction Stop
            Start-Sleep -Milliseconds 500
            if (Get-Process -Id $pid -ErrorAction SilentlyContinue) {
                Stop-Process -Id $pid -Force -ErrorAction Stop
            }
        } catch {
            $failed = $true
            Write-Host ("Failed to stop {0} (PID {1}): {2}" -f $name, $pid, $_.Exception.Message) -ForegroundColor Red
        }
    }

    if (-not $failed) {
        Write-Host ("Stopped {0}" -f $name) -ForegroundColor Green
        $stopped += $name
    } else {
        Write-Host ("Stop incomplete for {0}" -f $name) -ForegroundColor Yellow
    }
}

function Format-Names([string[]]$names) {
    if (-not $names -or $names.Count -eq 0) {
        return "None"
    }
    return ($names | Select-Object -Unique) -join ", "
}

Write-Host ""
Write-Host "Summary" -ForegroundColor Cyan
Write-Host ("Found: {0}" -f (Format-Names $found))
Write-Host ("Stopped: {0}" -f (Format-Names $stopped))
Write-Host ("Not running: {0}" -f (Format-Names $notRunning))
