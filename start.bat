@echo off
cd /d "%~dp0"
call "%~dp0Start-ProjectFlow.bat" %*
exit /b %ERRORLEVEL%
