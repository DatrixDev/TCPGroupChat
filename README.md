# TCPGroupChat

Ứng dụng chat Client–Server bằng **Java TCP**, tổ chức theo Maven multi-module, tách phần dùng chung, server và client. Client là ứng dụng desktop **Java Swing + FlatLaf**.

## Tính năng

- **Unicast**: gửi tin nhắn riêng tới một người dùng online; người gửi cũng thấy bản tin đã gửi.
- **Group Chat**: tham gia/rời phòng; chỉ thành viên hiện tại nhận tin nhắn nhóm.
- **Broadcast**: gửi tới tất cả client đang đăng nhập.
- Danh sách người dùng online và thông báo hệ thống.
- Giao tiếp TCP bằng JSON theo từng dòng (JSON Lines).

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

Yêu cầu JDK 21 và Maven. Kiểm tra Maven thực sự dùng Java 21 bằng lệnh `mvn -version`.

1. Mở terminal tại thư mục gốc và build toàn bộ module:
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
4. Chạy thêm client ở các terminal khác để demo. Mỗi client cần một tên người dùng khác nhau.

Mặc định server lắng nghe cổng **2006** và client kết nối tới `127.0.0.1:2006`. Biến môi trường `CHAT_PORT` đặt cổng server/client; `CHAT_HOST` đặt địa chỉ server mà client kết nối tới. Nếu chạy trên hai máy khác nhau, hãy đặt `CHAT_HOST` thành IP của máy chạy server và cho phép TCP qua cổng tương ứng trong firewall.

## Kịch bản demo

1. Mở 5 client với 5 tên khác nhau.
2. Cho client 1, 2, 3 nhấn **Join Group**.
3. Client 1 gửi tin nhắn Group: chỉ client 1, 2, 3 nhận tin.
4. Client 1 nhấn **Leave Group**. Sau đó client 2 gửi tin nhắn Group: chỉ client 2 và 3 nhận tin; client 1 và hai client ngoài nhóm không nhận.
5. Thử Unicast tới một người cụ thể và Broadcast để kiểm tra hai chế độ còn lại.

> Phòng chat mặc định tên `study`; danh sách người online và trạng thái thành viên nhóm được quản lý tập trung tại server.
