@echo off
cd /d "%~dp0"
call mvn clean install
if errorlevel 1 (
  echo Build that bai. Kiem tra JDK 17 va Maven.
  pause
  exit /b 1
)
echo Build thanh cong. Chay run-server.bat va run-client.bat.
pause
