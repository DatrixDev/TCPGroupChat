# TCPGroupChat

Ứng dụng chat Client–Server bằng **Java TCP**, phát triển theo phong cách dự án UDPMail: Maven multi-module, tách phần dùng chung, server và client; giao diện desktop dùng **Java Swing + FlatLaf**.

## Tính năng
- **Unicast**: gửi riêng tới một người dùng.
- **Group Chat**: Join/Leave phòng chat; chỉ thành viên hiện tại nhận tin nhắn nhóm.
- **Broadcast**: gửi tới tất cả client đang đăng nhập.
- Danh sách người dùng online và thông báo hệ thống.
- Giao tiếp TCP bằng JSON theo từng dòng.

## Công nghệ
- Java 21
- Maven
- TCP Socket
- Jackson Databind
- Swing + FlatLaf

## Cấu trúc
```
TCPGroupChat/
├── pom.xml
├── common/
├── server/
└── client/
```

## Build và chạy
Yêu cầu JDK 21 và Maven.

1. Mở terminal tại thư mục gốc và cài các module vào Maven local repository:
   ```bash
   mvn clean install
   ```
2. Mở terminal thứ nhất, chạy server:
   ```bash
   mvn -pl server exec:java -Dexec.mainClass="com.tcpgroupchat.server.ChatServer"
   ```
3. Mở terminal thứ hai, chạy client:
   ```bash
   mvn -pl client exec:java -Dexec.mainClass="com.tcpgroupchat.client.ClientApplication"
   ```
4. Chạy thêm client ở các terminal khác để demo.

Mặc định server lắng nghe cổng **2006**. Client kết nối tới `127.0.0.1:2006`. Có thể đặt biến môi trường `CHAT_HOST` và `CHAT_PORT` để đổi địa chỉ/cổng.

## Kịch bản demo
1. Mở 5 client với 5 tên khác nhau.
2. Cho client 1, 2, 3 nhấn **Join Group**.
3. Client 1 gửi tin nhắn Group: chỉ client 1, 2, 3 nhận tin.
4. Client 1 nhấn **Leave Group**. Sau đó client 2 gửi tin nhắn Group: chỉ client 2 và 3 nhận tin; client 1 và hai client ngoài nhóm không nhận.
5. Thử Unicast tới một người cụ thể và Broadcast để kiểm tra hai chế độ còn lại.

> Đây là phiên bản khởi đầu để phát triển bài tập lớn. Phòng chat mặc định có tên `study`; trạng thái thành viên được quản lý tập trung tại server.
