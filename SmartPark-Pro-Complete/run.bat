@echo off
setlocal
cd /d "%~dp0"
echo ========================================
echo       SmartPark Pro - Launcher
echo ========================================
where javac >nul 2>nul
if errorlevel 1 (
  echo ERROR: javac was not found.
  echo Install JDK 11 or newer and make sure its bin folder is on PATH.
  echo Check with: javac -version
  pause
  exit /b 1
)
echo Compiling Java files...
javac Main.java ParkingService.java Vehicle.java HttpUtils.java
if errorlevel 1 (
  echo.
  echo Compile failed. Make sure this folder contains all four .java files.
  pause
  exit /b 1
)
echo.
echo Starting SmartPark Pro at http://localhost:8080
echo Keep this window open while using the website.
java Main
if errorlevel 1 (
  echo.
  echo If port 8080 is already in use, open a new PowerShell terminal and run:
  echo   $env:PORT=8081
  echo   java Main
  pause
)
endlocal
