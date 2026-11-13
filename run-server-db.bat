@echo off
set "CHAT_DB_URL=jdbc:mysql://localhost:3306/tcpgroupchat?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true"
set "CHAT_DB_USER=tcpchat"
set "CHAT_DB_PASSWORD=tcpchat"
set "CHAT_DB_REQUIRED=true"
call run-server.bat
