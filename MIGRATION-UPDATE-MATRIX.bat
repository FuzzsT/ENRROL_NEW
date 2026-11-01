@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0MIGRATION-UPDATE-MATRIX.ps1" %*
exit /b %ERRORLEVEL%
