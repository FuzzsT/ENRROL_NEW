@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0WAIT-DOWNLOAD-MIGRATION-MATRIX.ps1" %*
exit /b %ERRORLEVEL%
