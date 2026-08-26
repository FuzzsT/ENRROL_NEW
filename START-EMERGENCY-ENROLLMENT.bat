@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0START-EMERGENCY-ENROLLMENT.ps1" %*
exit /b %ERRORLEVEL%
