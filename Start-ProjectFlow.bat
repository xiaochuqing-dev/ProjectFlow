@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call "%~dp0start-projectflow-embedded.bat" %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
