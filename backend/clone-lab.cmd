@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0clone-lab\start.ps1" %*
exit /b %errorlevel%
