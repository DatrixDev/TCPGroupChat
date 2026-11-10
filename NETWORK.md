# Mạng client–server qua TCP

Client dùng IP và cổng người dùng nhập trên màn hình đăng nhập để mở kết nối tới server. Sau khi xác thực, cùng socket TCP đó mang các yêu cầu quản lý nhóm, tin nhắn, sự kiện và ACK. Mỗi thông điệp là một dòng JSON UTF-8.

| Chế độ | Server chuyển tin qua socket TCP tới |
| --- | --- |
| Unicast | Một người nhận online |
| Group | Các thành viên hiện tại của nhóm |
| Broadcast | Mọi client đã đăng nhập, gồm người gửi |

`ClientConnection.open` kết nối tới địa chỉ đã nhập. `ClientConnection.send` ghi thông điệp lên kết nối đó. `ChatServer.route` chọn người nhận từ các phiên đã xác thực và đưa tin vào hàng đợi gửi của từng phiên. ACK xác nhận server xử lý yêu cầu định tuyến. Nó không xác nhận người nhận đã đọc tin.

## Chạy trên LAN

1. Build lại cả client và server bằng `mvn clean install`.
2. Trên máy server, chạy ứng dụng, kiểm tra **IP Server** và **Cổng TCP** trên dashboard rồi nhấn **Khởi động**.
3. Trên mỗi máy client, tự điền IP LAN của máy server và cổng TCP tương ứng vào form đăng nhập. Nếu chạy cùng máy, điền `127.0.0.1`.
4. Nếu Windows Firewall chặn kết nối, cho phép cổng TCP của server trên mạng riêng. Cổng mặc định là `2006`.

Các bước kiểm tra Gửi riêng, Gửi nhóm, Leave và Gửi tất cả nằm trong [kịch bản demo](README.md#kịch-bản-demo-5-client).
