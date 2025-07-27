@echo off
setlocal enabledelayedexpansion

echo Freeing port 8081 and cleaning up processes...

REM Kill any existing processes on port 8081
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8081') do (
    taskkill /f /pid %%a 2>nul
)

REM Kill any existing Gradle processes
taskkill /f /im java.exe /fi "WINDOWTITLE eq *gradle*" 2>nul
taskkill /f /im java.exe /fi "COMMANDLINE eq *gradle*" 2>nul

timeout /t 2 /nobreak >nul

echo Starting the server...
start /b gradlew.bat :server:run

echo Waiting for server to start...
timeout /t 5 /nobreak >nul

echo Opening browser...
start http://localhost:8081

echo Server is running. Press Ctrl+C to stop.
echo.
echo To stop the server, close this window or press Ctrl+C
pause 