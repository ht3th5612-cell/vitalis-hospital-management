@echo off
setlocal
cd /d "%~dp0"

call build.bat
if errorlevel 1 exit /b 1

echo.
echo Starting VITALIS...
java -cp "bin;lib/*" app.Launcher
endlocal
