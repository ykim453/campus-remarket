@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat bootRun
if errorlevel 1 pause
