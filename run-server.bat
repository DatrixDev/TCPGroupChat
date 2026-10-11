@echo off
cd /d "%~dp0"
set "APP_JAR=dist\TCPGroupChat-server.jar"
if exist "server\target\TCPGroupChat-server.jar" set "APP_JAR=server\target\TCPGroupChat-server.jar"
if not exist "%APP_JAR%" (
  echo Hay chay build.bat truoc.
  pause
  exit /b 1
)
java -jar "%APP_JAR%"
if errorlevel 1 pause
