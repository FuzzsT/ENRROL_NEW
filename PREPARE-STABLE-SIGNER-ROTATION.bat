@echo off
setlocal
set "ROOT=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%PREPARE-STABLE-SIGNER-ROTATION.ps1" %*
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" echo [ERROR] Stable signer rotation preparation failed with exit code %RC%.
exit /b %RC%
