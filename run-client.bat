@echo off
cd /d "%~dp0"
set "APP_JAR=dist\TCPGroupChat-client.jar"
if exist "client\target\TCPGroupChat-client.jar" set "APP_JAR=client\target\TCPGroupChat-client.jar"
if not exist "%APP_JAR%" (
  echo Hay chay build.bat truoc.
  pause
  exit /b 1
)
java -jar "%APP_JAR%"
if errorlevel 1 pause
