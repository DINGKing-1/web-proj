# 优品汇 - 优雅停止脚本
# 先尝试 API 关闭，再用 SIGTERM，最后才强制终止

$port = 8080

# 1. 尝试通过 API 优雅关闭（触发 ShutdownHook；需 X-Requested-With 防 CSRF）
$headers = @{ 'X-Requested-With' = 'XMLHttpRequest' }
Write-Host "尝试优雅关闭..." -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "http://localhost:$port/internal/shutdown" -Method POST -Headers $headers -TimeoutSec 3 | Out-Null
    Write-Host "已发送关闭指令，等待端口释放..." -ForegroundColor Green
    Start-Sleep -Seconds 5
} catch {
    try {
        Invoke-RestMethod -Uri "http://localhost:$port/api/internal/shutdown" -Method POST -Headers $headers -TimeoutSec 3 | Out-Null
        Write-Host "已发送关闭指令，等待端口释放..." -ForegroundColor Green
        Start-Sleep -Seconds 5
    } catch {
        Write-Host "API 关闭失败，尝试直接终止" -ForegroundColor Yellow
    }
}

# 2. 查找占用端口的进程
$procs = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq 'Listen' } |
    Select-Object -ExpandProperty OwningProcess -Unique

if ($procs) {
    foreach ($pid in $procs) {
        Write-Host "终止进程 PID: $pid" -ForegroundColor Red
        # 不用 -Force，发送 SIGTERM 让 JVM 执行 ShutdownHook
        Stop-Process -Id $pid -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 5
}

# 3. 检查端口是否释放，没释放则强制终止
$remaining = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq 'Listen' }
if ($remaining) {
    Write-Host "端口仍占用，强制终止..." -ForegroundColor Red
    foreach ($pid in ($remaining | Select-Object -ExpandProperty OwningProcess -Unique)) {
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}

# 4. 验证
$check = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq 'Listen' }
if ($check) {
    Write-Host "端口 $port 仍被占用" -ForegroundColor Red
} else {
    Write-Host "端口 $port 已释放" -ForegroundColor Green
}
