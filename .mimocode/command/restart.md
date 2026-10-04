---
description: "Restart the Spring Boot application. Stops any running instance on port 8080, then starts a new one."
---

Restart the Spring Boot application:

1. Find Java process on port 8080:
   ```powershell
   $proc = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
   ```

2. If found, gracefully stop it (try API first, then force kill):
   ```powershell
   if ($proc) {
       try { Invoke-RestMethod -Uri "http://localhost:8080/internal/shutdown" -Method POST -Headers @{ 'X-Requested-With'='XMLHttpRequest' } -TimeoutSec 3 } catch {}
       Start-Sleep -Seconds 3
       foreach ($pid in $proc) { Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue }
       Start-Sleep -Seconds 2
   }
   ```

3. Verify port 8080 is released:
   ```powershell
   $check = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
   if ($check) { Write-Warning "Port 8080 still in use"; exit 1 }
   ```

4. Start the application:
   ```powershell
   $jar = Get-ChildItem E:\mimo-worker\javaweb\target\*.jar | Select-Object -First 1
   Start-Process -FilePath "java" -ArgumentList "-jar", $jar.FullName -WindowStyle Hidden
   ```

5. Wait 8 seconds and verify it's running:
   ```powershell
   Start-Sleep -Seconds 8
   $running = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
   if ($running) { Write-Host "Application started successfully" } else { Write-Warning "Application failed to start" }
   ```
