$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$services = @(
    @{ Name = "pricing-service"; Port = 9092 },
    @{ Name = "inventory-service"; Port = 9093 },
    @{ Name = "analytics-service"; Port = 9094 },
    @{ Name = "order-service"; Port = 9091 },
    @{ Name = "api-gateway"; Port = 8080 }
)

Write-Host "Building required modules first..." -ForegroundColor Cyan
$env:MAVEN_OPTS = "-Xmx512m"
& .\mvnw.cmd -q -DskipTests clean install

Write-Host ""
Write-Host "Starting services in separate PowerShell windows..." -ForegroundColor Cyan

foreach ($service in $services) {
    $modulePom = Join-Path $repoRoot "$($service.Name)\pom.xml"
    $command = "Set-Location '$repoRoot'; `$env:MAVEN_OPTS='-Xmx512m'; .\mvnw.cmd -f '$modulePom' spring-boot:run"
    Start-Process powershell -ArgumentList @("-NoExit", "-Command", $command)
    Write-Host ("Started {0} on port {1}" -f $service.Name, $service.Port) -ForegroundColor Green
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "REST gateway: http://localhost:8080" -ForegroundColor Yellow
Write-Host "Wait until each window prints 'Started ...Application' before testing requests." -ForegroundColor Yellow
