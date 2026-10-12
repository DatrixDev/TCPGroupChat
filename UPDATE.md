# Dùng bản hoàn thiện

## Cập nhật repository từ GitHub

Tại thư mục TCPGroupChat trên máy bạn, lưu/commit thay đổi riêng trước khi cập nhật:

```bash
git pull origin main
mvn clean install
```

Trong IntelliJ: Reload Maven, chọn JDK 17 trở lên, chạy `com.tcpgroupchat.server.ChatServer`, nhấn Khởi động, rồi chạy nhiều instance `com.tcpgroupchat.client.ClientApplication`.

Trên Windows có thể chạy `build.bat`, sau đó `run-server.bat` và `run-client.bat`. Đăng ký 5 tài khoản mới và làm theo kịch bản demo trong README.

## Dùng bản ZIP đã bàn giao

ZIP có mã nguồn và hai JAR chạy sẵn trong `dist`; chỉ cần Java 17 trở lên để chạy hai file `.bat`. Repository GitHub chứa mã nguồn; hãy build trước khi chạy.

ZIP kèm `TCPGroupChat-update.patch` tạo từ commit `ebeab15`. Nếu đã cập nhật từ GitHub thì không cần áp dụng lại patch. Nếu repository có thay đổi riêng, kiểm tra và giải quyết xung đột trước khi cập nhật.

Việc cập nhật GitHub không tự thay đổi project đang mở trong IntelliJ trên máy bạn; cần thực hiện `git pull` hoặc thao tác Update Project.
