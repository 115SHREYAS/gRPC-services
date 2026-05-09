param(
    [int]$StopTimeoutSeconds = 5
)

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
$separatorPattern = "[\\/]"
$javaProcesses = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(\.exe)?$' -and $_.CommandLine
}

$found = @()
$stopped = @()
$notRunning = @()

foreach ($service in $services) {
    $name = $service.Name
    $servicePattern = [Regex]::Escape($name)
    $servicePathPrefix = "$repoPattern$separatorPattern+$servicePattern"
    $serviceBoundary = "(?:$separatorPattern|\.jar|\s)"
    # Matches repoRoot/service-name/ or repoRoot/service-name.jar or repoRoot/service-name<space/end>
    $serviceRegex = "(?i)$servicePathPrefix(?:$serviceBoundary|$)"
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

    $failedPids = @()
    foreach ($pid in $pids) {
        try {
            Stop-Process -Id $pid -ErrorAction Stop
            $timedOut = $false
            try {
                Wait-Process -Id $pid -Timeout $StopTimeoutSeconds -ErrorAction Stop
            } catch {
                $timedOut = $true
            }
            if (Get-Process -Id $pid -ErrorAction SilentlyContinue) {
                if ($timedOut) {
                    Write-Host ("Graceful stop timed out for {0} (PID {1}); forcing stop." -f $name, $pid) -ForegroundColor Yellow
                }
                Stop-Process -Id $pid -Force -ErrorAction Stop
            }
        } catch {
            $failedPids += $pid
            Write-Host ("Failed to stop {0} (PID {1}): {2}" -f $name, $pid, $_.Exception.Message) -ForegroundColor Red
        }
    }

    if ($failedPids.Count -eq 0) {
        Write-Host ("Stopped {0}" -f $name) -ForegroundColor Green
        $stopped += $name
    } else {
        Write-Host ("Stop incomplete for {0}. Failed PIDs: {1}" -f $name, ($failedPids -join ", ")) -ForegroundColor Yellow
    }
}

function Format-ServiceList([string[]]$names) {
    if (-not $names -or $names.Count -eq 0) {
        return "None"
    }
    return ($names | Select-Object -Unique) -join ", "
}

Write-Host ""
Write-Host "Summary" -ForegroundColor Cyan
Write-Host ("Found: {0}" -f (Format-ServiceList $found))
Write-Host ("Stopped: {0}" -f (Format-ServiceList $stopped))
Write-Host ("Not running: {0}" -f (Format-ServiceList $notRunning))
