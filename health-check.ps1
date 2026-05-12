param(
    [int]$TimeoutSeconds = 2
)

$ErrorActionPreference = "Stop"

$services = @(
    @{ Name = "api-gateway"; Port = 8080 },
    @{ Name = "order-service"; Port = 8081 },
    @{ Name = "pricing-service"; Port = 8082 },
    @{ Name = "inventory-service"; Port = 8083 },
    @{ Name = "analytics-service"; Port = 8084 }
)

function Test-ServiceHealth($service, [int]$timeoutSeconds) {
    $uri = "http://localhost:{0}/actuator/health" -f $service.Port
    try {
        $response = Invoke-RestMethod -Method Get -Uri $uri -TimeoutSec $timeoutSeconds
        return [pscustomobject]@{
            Name = $service.Name
            Port = $service.Port
            Reachable = $true
            Status = $response.status
            Uri = $uri
        }
    } catch {
        return [pscustomobject]@{
            Name = $service.Name
            Port = $service.Port
            Reachable = $false
            Status = "DOWN"
            Uri = $uri
        }
    }
}

$results = foreach ($service in $services) {
    Test-ServiceHealth -service $service -timeoutSeconds $TimeoutSeconds
}

Write-Host ""
Write-Host "Service Health Summary" -ForegroundColor Cyan
Write-Host "----------------------" -ForegroundColor Cyan

foreach ($result in $results) {
    if ($result.Reachable -and $result.Status -eq "UP") {
        Write-Host ("UP   {0} ({1}) -> {2}" -f $result.Name, $result.Port, $result.Uri) -ForegroundColor Green
    } else {
        Write-Host ("DOWN {0} ({1}) -> {2}" -f $result.Name, $result.Port, $result.Uri) -ForegroundColor Yellow
    }
}

$upCount = ($results | Where-Object { $_.Reachable -and $_.Status -eq "UP" }).Count
Write-Host ""
Write-Host ("Healthy services: {0}/{1}" -f $upCount, $results.Count) -ForegroundColor Cyan
