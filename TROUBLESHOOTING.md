# WebSocket Mock Server - Troubleshooting Guide

## Issue: Dashboard Not Showing / Server Fails to Start

### Problem Description
The server fails to start with exit code `-1073741510` and the dashboard doesn't load. This typically happens when:
- Java version is too old (Java 8 or Java 9)
- JAVA_HOME is pointing to an incompatible Java version
- The Ktor server requires Java 11 or later

### Symptoms
- Server times out after 60 seconds
- Exit code: `-1073741510`
- Error message: "Server process exited unexpectedly"
- Browser opens but shows "Unable to connect" or similar error
- No actual server process listening on port 8081

### Root Cause
Ktor 2.3.7 (used in this project) requires **Java 11 or later**. If your system has only Java 8 or Java 9 installed, the server will crash immediately on startup.

---

## Solution: Install Java 17

### Step 1: Check Your Current Java Version

Open PowerShell or Command Prompt and run:

```powershell
java -version
```

If you see Java version 1.8, 8, or 9, you need to install Java 17.

### Step 2: Install Java 17 Using Winget

**Option A: Microsoft OpenJDK 17 (Recommended)**

```powershell
winget install Microsoft.OpenJDK.17 --accept-source-agreements --accept-package-agreements
```

**Option B: Eclipse Temurin JDK 17**

```powershell
winget install EclipseAdoptium.Temurin.17.JDK --accept-source-agreements --accept-package-agreements
```

**Option C: Azul Zulu JDK 17**

```powershell
winget install Azul.Zulu.17.JDK --accept-source-agreements --accept-package-agreements
```

### Step 3: Find Your Java 17 Installation Path

After installation, find the exact installation path:

```powershell
# Search all common Java installation locations
Get-ChildItem "C:\Program Files\Microsoft\jdk-*", "C:\Program Files\Eclipse Adoptium\*", "C:\Program Files\Zulu\*", "C:\Program Files\Java\*" -Directory -ErrorAction SilentlyContinue | Select-Object FullName, Name

# Or for specific vendors:
# Microsoft OpenJDK
Get-ChildItem "C:\Program Files\Microsoft\jdk-*" -Directory | Select-Object FullName

# Eclipse Temurin
Get-ChildItem "C:\Program Files\Eclipse Adoptium\*" -Directory | Select-Object FullName

# Azul Zulu
Get-ChildItem "C:\Program Files\Zulu\*" -Directory | Select-Object FullName
```

**Important**: Copy the **FullName** path from the output. You'll need it for the next step.

### Step 4: Set JAVA_HOME (Temporary)

Set the JAVA_HOME environment variable for your current PowerShell session using the path from Step 3:

```powershell
# ⚠️ IMPORTANT: Replace this with YOUR actual path from Step 3!
# Example for Microsoft OpenJDK 17:
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"

# Add Java to PATH
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Verify it's working
java -version
# Should show: openjdk version "17.x.x" or higher (not 8 or 9)
```

**Examples of different paths:**
- Microsoft: `C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot`
- Eclipse: `C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot`
- Azul: `C:\Program Files\Zulu\zulu-17`

### Step 5: Set JAVA_HOME (Permanent)

To make JAVA_HOME permanent across all sessions:

**Option 1: Using PowerShell (Run as Administrator)**

```powershell
# ⚠️ Replace with YOUR actual Java path from Step 3!
$javaPath = "C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"

# Set JAVA_HOME permanently
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', $javaPath, [System.EnvironmentVariableTarget]::Machine)

# Add to PATH permanently
$currentPath = [System.Environment]::GetEnvironmentVariable('PATH', [System.EnvironmentVariableTarget]::Machine)
$newPath = "$javaPath\bin;$currentPath"
[System.Environment]::SetEnvironmentVariable('PATH', $newPath, [System.EnvironmentVariableTarget]::Machine)

# Verify (restart terminal first)
Write-Host "JAVA_HOME set to: $javaPath"
Write-Host "Restart your terminal/IDE for changes to take effect"
```

**Option 2: Using Windows GUI**

1. Right-click on "This PC" → Properties
2. Click "Advanced system settings"
3. Click "Environment Variables"
4. Under "System variables", click "New"
5. Variable name: `JAVA_HOME`
6. Variable value: **[Paste YOUR Java path from Step 3]**
   - Example: `C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot`
7. Click OK
8. Find "Path" in System variables, click "Edit"
9. Add new entry: `%JAVA_HOME%\bin`
10. Click OK on all dialogs
11. **Restart your terminal/IDE for changes to take effect**

### Step 6: Start the Server

Now start the server:

```powershell
.\start-server.ps1
```

Or start it directly with Gradle:

```powershell
.\gradlew.bat :server:run
```

The dashboard should now be accessible at: **http://localhost:8081**

---

## Alternative: Manual Start with Correct Java

If you don't want to change system environment variables, you can always start the server by specifying JAVA_HOME in the command:

```powershell
# Replace with YOUR actual Java path
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :server:run
```

**Tip**: You can create a batch file or PowerShell script with these lines so you don't have to type them every time.

---

## Verification Checklist

After applying the fix, verify:

1. ✅ Java 17 is installed:
   ```powershell
   java -version
   # Should show: openjdk version "17.x.x"
   ```

2. ✅ JAVA_HOME is set correctly:
   ```powershell
   echo $env:JAVA_HOME
   # Should point to your Java 17 installation
   ```

3. ✅ Server starts without errors:
   ```powershell
   .\gradlew.bat :server:run
   # Should show: "Responding at http://127.0.0.1:8081"
   ```

4. ✅ Port 8081 is listening:
   ```powershell
   Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue
   # Should show a listening connection
   ```

5. ✅ Dashboard loads in browser:
   - Open: http://localhost:8081
   - Should see the WebSocket Mock Server dashboard

---

## Common Issues After Fix

### Issue: "JAVA_HOME is set to an invalid directory"

**Solution:** Make sure the path exists and matches your installation:
```powershell
Test-Path "C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
# Should return: True
```

### Issue: Server still uses old Java version

**Solution:** Close all terminal windows and restart your IDE to pick up the new environment variables.

### Issue: Port 8081 already in use

**Solution:** Kill existing processes on port 8081:
```powershell
Get-NetTCPConnection -LocalPort 8081 | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force }
```

---

## Summary

**The fix was simple:** Install Java 17 (or any Java 11+) and ensure JAVA_HOME points to it. The server requires Java 11+ to run Ktor 2.3.7, which is the web framework powering this mock server.

**Quick Fix Command (for new PC):**
```powershell
# 1. Install Java 17
winget install Microsoft.OpenJDK.17 --accept-source-agreements --accept-package-agreements

# 2. Find your Java installation path
Get-ChildItem "C:\Program Files\Microsoft\jdk-*", "C:\Program Files\Eclipse Adoptium\*", "C:\Program Files\Java\*" -Directory -ErrorAction SilentlyContinue | Select-Object FullName

# 3. Set JAVA_HOME for current session (replace with YOUR path from step 2)
$env:JAVA_HOME = "YOUR_JAVA_PATH_HERE"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 4. Verify
java -version

# 5. Start server
.\gradlew.bat :server:run
```

For permanent fix, set JAVA_HOME as a system environment variable (see Step 5 above).

