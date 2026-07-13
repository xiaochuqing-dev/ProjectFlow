@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-projectflow-embedded.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"
if /I "%~1"=="-CheckOnly" exit /b %EXIT_CODE%
pause
exit /b %EXIT_CODE%
