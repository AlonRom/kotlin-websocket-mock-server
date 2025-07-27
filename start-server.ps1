# PowerShell script to start the WebSocket server on Windows

Write-Host "Freeing port 8081 and cleaning up processes..." -ForegroundColor Yellow

# Kill any existing processes on port 8081
try {
    $processes = Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess
    foreach ($pid in $processes) {
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        Write-Host "Killed process on port 8081: $pid" -ForegroundColor Red
    }
} catch {
    Write-Host "No processes found on port 8081" -ForegroundColor Green
}

# Kill any existing Gradle processes
try {
    Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -eq "java" -and $_.CommandLine -like "*gradle*" } | Stop-Process -Force
} catch {
    Write-Host "No Gradle processes found" -ForegroundColor Green
}

Start-Sleep -Seconds 2

Write-Host "Starting the server..." -ForegroundColor Green
$serverJob = Start-Job -ScriptBlock {
    Set-Location $using:PWD
    .\gradlew.bat :server:run
}

Write-Host "Waiting for server to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host "Opening browser..." -ForegroundColor Green
Start-Process "http://localhost:8081"

Write-Host "Server is running!" -ForegroundColor Green
Write-Host "To stop the server, press Ctrl+C or close this window" -ForegroundColor Cyan

try {
    # Keep the script running and monitor the server
    while ($true) {
        $jobStatus = Get-Job -Id $serverJob.Id -ErrorAction SilentlyContinue
        if ($jobStatus -eq $null -or $jobStatus.State -eq "Failed") {
            Write-Host "Server stopped unexpectedly" -ForegroundColor Red
            break
        }
        Start-Sleep -Seconds 1
    }
} catch {
    Write-Host "Stopping server..." -ForegroundColor Yellow
} finally {
    # Cleanup
    if ($serverJob) {
        Stop-Job -Job $serverJob -ErrorAction SilentlyContinue
        Remove-Job -Job $serverJob -ErrorAction SilentlyContinue
    }
    Write-Host "Server stopped" -ForegroundColor Red
} 