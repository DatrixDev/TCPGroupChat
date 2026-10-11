# TCPGroupChat

Ứng dụng chat Client–Server bằng **Java TCP**, tổ chức theo Maven multi-module, tách phần dùng chung, server và client. Cả **Server và Client đều có giao diện desktop Java Swing + FlatLaf**; giao diện quản trị Server được thiết kế theo hướng dashboard của dự án UDPMail gốc.

## Tính năng

- **Server Dashboard**: khởi động/dừng server, trạng thái hoạt động, IP máy chạy server, cổng TCP, danh sách client online và nhật ký kết nối/tin nhắn.
- **Unicast**: gửi tin nhắn riêng tới một người dùng online; người gửi cũng thấy bản tin đã gửi.
- **Group Chat**: tham gia/rời phòng; chỉ thành viên hiện tại nhận tin nhắn nhóm.
- **Broadcast**: gửi tới tất cả client đang đăng nhập.
- Danh sách người dùng online và thông báo hệ thống.
- Giao tiếp TCP bằng JSON theo từng dòng (JSON Lines).

## Công nghệ

- Java 21
- Maven multi-module
- TCP Socket
- Jackson Databind
- Java Swing + FlatLaf

## Cấu trúc

```
TCPGroupChat/
├── pom.xml
├── common/
├── server/
│   └── src/main/java/com/tcpgroupchat/server/
│       ├── ChatServer.java
│       └── ServerFrame.java
└── client/
```

## Build và chạy

Yêu cầu JDK 21 và Maven. Kiểm tra Maven thực sự dùng Java 21 bằng lệnh `mvn -version`.

1. Mở terminal tại thư mục gốc và build toàn bộ module:

   ```bash
   mvn clean install
   ```

2. Mở IntelliJ và chạy class `com.tcpgroupchat.server.ChatServer`. Cửa sổ **TCPGroupChat | Server Dashboard** sẽ mở; nhấn **Khởi động Server** để lắng nghe cổng TCP 2006.
3. Chạy class `com.tcpgroupchat.client.ClientApplication` để mở client. Nhập tên người dùng từ 2–20 ký tự (chữ không dấu, số, `_` hoặc `-`).
4. Chạy thêm client instance với tên người dùng khác để thử chat nhiều người.

Có thể chạy qua Maven:

```bash
mvn -pl server exec:java -Dexec.mainClass="com.tcpgroupchat.server.ChatServer"
mvn -pl client exec:java -Dexec.mainClass="com.tcpgroupchat.client.ClientApplication"
```

Lệnh đầu tiên mở dashboard Server; bạn vẫn cần nhấn **Khởi động Server** trong cửa sổ đó trước khi kết nối client.

Mặc định server lắng nghe cổng **2006** và client kết nối tới `127.0.0.1:2006`. Biến môi trường `CHAT_PORT` đặt cổng; `CHAT_HOST` đặt địa chỉ server mà client kết nối tới. Nếu chạy trên hai máy khác nhau, đặt `CHAT_HOST` thành IP của máy chạy server và cho phép TCP qua cổng tương ứng trong firewall.

## Kịch bản demo

1. Mở Server Dashboard và nhấn **Khởi động Server**.
2. Mở 5 client với 5 tên khác nhau.
3. Cho client 1, 2, 3 nhấn **Join Group**.
4. Client 1 gửi tin nhắn Group: chỉ client 1, 2, 3 nhận tin.
5. Client 1 nhấn **Leave Group**. Sau đó client 2 gửi tin nhắn Group: chỉ client 2 và 3 nhận tin; client 1 và hai client ngoài nhóm không nhận.
6. Thử Unicast tới một người cụ thể và Broadcast để kiểm tra hai chế độ còn lại.

> Phòng chat mặc định tên `study`; danh sách người online và trạng thái thành viên nhóm được quản lý tập trung tại server.
