@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0AUDIT-MIGRATION-SECRETS.ps1" %*
exit /b %ERRORLEVEL%
