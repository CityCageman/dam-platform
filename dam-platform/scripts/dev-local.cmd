@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev-local.ps1" %*
set RC=%ERRORLEVEL%
if "%~1"=="" pause
exit /b %RC%
