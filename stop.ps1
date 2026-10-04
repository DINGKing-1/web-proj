# YouPinHui - graceful stop script
# Try API shutdown first (needs X-Requested-With for CSRF), then SIGTERM, then force kill.

$port = 8080
$shutdownHeaders = @{ 'X-Requested-With' = 'XMLHttpRequest' }

Write-Host "Trying graceful shutdown..." -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "http://localhost:$port/internal/shutdown" -Method POST -Headers $shutdownHeaders -TimeoutSec 3 | Out-Null
    Write-Host "Shutdown signal sent, waiting for port release..." -ForegroundColor Green
    Start-Sleep -Seconds 5
} catch {
    try {
        Invoke-RestMethod -Uri "http://localhost:$port/api/internal/shutdown" -Method POST -Headers $shutdownHeaders -TimeoutSec 3 | Out-Null
        Write-Host "Shutdown signal sent, waiting for port release..." -ForegroundColor Green
        Start-Sleep -Seconds 5
    } catch {
        Write-Host "API shutdown failed, will terminate process" -ForegroundColor Yellow
    }
}

$procs = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq 'Listen' } |
    Select-Object -ExpandProperty OwningProcess -Unique

if ($procs) {
    foreach ($procId in $procs) {
        Write-Host "Stopping PID: $procId" -ForegroundColor Red
        Stop-Process -Id $procId -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 5
}

$remaining = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq 'Listen' }
if ($remaining) {
    Write-Host "Port still busy, force killing..." -ForegroundColor Red
    foreach ($procId in ($remaining | Select-Object -ExpandProperty OwningProcess -Unique)) {
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}

$check = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq 'Listen' }
if ($check) {
    Write-Host "Port $port is still in use" -ForegroundColor Red
} else {
    Write-Host "Port $port released" -ForegroundColor Green
}
