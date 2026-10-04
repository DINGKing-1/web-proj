# 优品汇：改完后一键重启并打开页面
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File .\restart.ps1

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$port = 8080
$uri = "http://127.0.0.1:$port/"

Write-Host "[1/4] 构建前端..." -ForegroundColor Cyan
Push-Location (Join-Path $root 'frontend')
& node node_modules\npm\bin\npm-cli.js run build
if ($LASTEXITCODE -ne 0) {
  # 回退到 PATH 上的 npm
  npm run build
}
Pop-Location

Write-Host "[2/4] 停止旧服务..." -ForegroundColor Cyan
$shutdownHeaders = @{ 'X-Requested-With' = 'XMLHttpRequest' }
try {
  Invoke-RestMethod -Uri "http://localhost:$port/internal/shutdown" -Method POST -Headers $shutdownHeaders -TimeoutSec 2 | Out-Null
  Start-Sleep -Seconds 2
} catch {
  try { Invoke-RestMethod -Uri "http://localhost:$port/api/internal/shutdown" -Method POST -Headers $shutdownHeaders -TimeoutSec 2 | Out-Null } catch { }
  Start-Sleep -Seconds 2
}
Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
  Where-Object { $_.State -eq 'Listen' } |
  Select-Object -ExpandProperty OwningProcess -Unique |
  ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 1

Write-Host "[3/4] 启动后端..." -ForegroundColor Cyan
$mvn = Join-Path $root '.maven\apache-maven-3.9.6\bin\mvn.cmd'
Start-Process -FilePath $mvn -ArgumentList @('spring-boot:run') -WorkingDirectory $root -WindowStyle Hidden

Write-Host "[4/4] 等待服务就绪..." -ForegroundColor Cyan
$up = $false
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Seconds 2
  try {
    $r = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 2
    if ($r.StatusCode -eq 200) { $up = $true; break }
  } catch { }
}

if ($up) {
  Write-Host "服务已就绪：$uri" -ForegroundColor Green
  Start-Process $uri
} else {
  Write-Host "启动超时，请手动查看日志" -ForegroundColor Yellow
}
