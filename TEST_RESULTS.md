# Kiểm tra bản hoàn thiện

Ngày kiểm tra: 11/10/2026. Mã gốc: commit `ebeab15` của TCPGroupChat.

- `mvn clean install`: **BUILD SUCCESS**, Java 17.
- JUnit: **10 tests, 0 failures, 0 errors, 0 skipped**.
- Các test dùng socket TCP thật, dữ liệu tài khoản tạo trong thư mục tạm.

Các tình huống đã kiểm tra:

1. Năm client, ba người Join; chỉ ba người nhận Group.
2. Người gửi Leave rồi vẫn gửi được; chỉ hai người còn Join nhận Group.
3. Unicast chỉ người nhận nhận bản tin; Broadcast cả năm nhận.
4. Hai nhóm độc lập; Join lặp không làm tăng số thành viên.
5. Sai mật khẩu, đăng nhập trùng, giả mạo sender; mật khẩu không nằm trong file tài khoản/log.
6. Chưa đăng nhập, JSON sai, nhóm không hợp lệ, đích offline, nhóm rỗng, tin rỗng/quá dài.
7. Ngắt kết nối dọn thành viên và cho phép đăng nhập lại với mã phiên mới.
8. Dừng đóng tất cả socket; tạo mới đối tượng server vẫn đăng nhập bằng tài khoản đã lưu.
9. TCP frame bị chia nhỏ/gộp; frame quá lớn bị ngắt; ngắt riêng một phiên không ảnh hưởng client khác.
10. Năm client gửi đồng thời tổng cộng 100 bản tin Broadcast; mỗi client nhận đủ 100 JSON hợp lệ.

Đã chạy thêm giao diện Swing thật với 5 client trên màn hình X11 ảo:

- Đăng ký bằng nút trên màn hình, chuyển về đăng nhập, đăng nhập thành công.
- Join/Leave bằng nút và đối chiếu trạng thái, danh sách thành viên.
- Nút Gửi gửi đúng Group; Enter gửi đúng Unicast; Broadcast đến cả 5.
- Người ngoài nhóm thấy bản sao ĐÃ GỬI và ACK, không nhận lại bản tin Group.
- Mất kết nối vô hiệu hóa nút gửi.
- Kiểm tra ảnh giao diện kích thước đầy đủ và tối thiểu sau khi chỉnh bố cục.

Phạm vi kiểm tra: chạy trên Linux với Java 17; chưa chạy thực tế trên Windows hoặc hai máy LAN. File `.bat` dành cho Windows dùng hai JAR đã đóng gói; kịch bản LAN nằm trong README.
