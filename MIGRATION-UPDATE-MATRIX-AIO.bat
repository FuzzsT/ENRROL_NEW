@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0MIGRATION-UPDATE-MATRIX-AIO.ps1" %*
exit /b %ERRORLEVEL%
