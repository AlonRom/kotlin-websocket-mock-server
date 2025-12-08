# Quick Fix - Dashboard Not Showing

## The Problem
Server was crashing because it was trying to run with **Java 9**, but the project requires **Java 11 or later**.

## The Solution (3 Steps)

### 1. Install Java 17
```powershell
winget install Microsoft.OpenJDK.17 --accept-source-agreements --accept-package-agreements
```

### 2. Find Your Java Installation Path
```powershell
# Find all Java installations
Get-ChildItem "C:\Program Files\Microsoft\jdk-*", "C:\Program Files\Eclipse Adoptium\*", "C:\Program Files\Java\*" -Directory -ErrorAction SilentlyContinue | Select-Object FullName
```

Copy the path that shows Java 17 (or higher), then set JAVA_HOME:

```powershell
# Replace with YOUR actual Java path from the command above
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Verify it's correct
java -version
# Should show: openjdk version "17.x.x" or higher
```

### 3. Start the Server
```powershell
.\gradlew.bat :server:run
```

## Access Dashboard
Open browser: **http://localhost:8081**

---

## Make JAVA_HOME Permanent (Optional)

First, find your Java path (see step 2 above), then run PowerShell as Administrator:

```powershell
# Replace with YOUR actual Java path
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot', [System.EnvironmentVariableTarget]::Machine)
```

Then restart your terminal/IDE.

---

**For detailed troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md)**

