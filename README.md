# TCPGroupChat — Chat Client–Server

Đồ án lập trình mạng bằng **Java 17, TCP Socket, Swing, FlatLaf và JSON Lines**. Giữ mô hình desktop client/server và phong cách xanh navy, dashboard, icon SVG của UDPMail. Không cần Spring Boot, web server hay cơ sở dữ liệu ngoài.

## Chức năng đã hoàn thiện

**Client**

- Màn hình đăng ký/đăng nhập với tài khoản, mật khẩu, xác nhận mật khẩu và IP/cổng server.
- Một tài khoản chỉ có một phiên online. Tên đăng nhập không phân biệt hoa/thường, chuẩn hóa về chữ thường.
- Giao diện navy theo UDPMail: menu trái, danh sách nhóm/client, bong bóng chat, ba nút chế độ gửi. Nhật ký phiên mở từ menu.
- Hiện IP máy client ở góc dưới bên phải, tên tài khoản, địa chỉ server, thời gian đăng nhập; mã phiên nằm trong tooltip của dòng thông tin và nhật ký phiên.
- Danh sách client online cập nhật khi đăng nhập/ngắt kết nối; chọn một client để gửi riêng.
- Chọn chế độ **Unicast / Group / Broadcast**. Nút Gửi và Enter dùng đúng chế độ đang chọn.
- Xem nhóm bất kỳ, Join, Leave, danh sách thành viên và trạng thái đã Join/ngoài nhóm do server xác nhận.
- Nhật ký riêng cho phiên: gửi yêu cầu, nhận tin, ACK, lỗi, mất kết nối.
- Đăng xuất về màn hình đăng nhập; mất kết nối thì vô hiệu hóa thao tác gửi, giữ cuộc trò chuyện để xem.

**Server**

- Dashboard theo UDPMail: ba thẻ thống kê, bảng người dùng và nhật ký xếp dọc. Khởi động/dừng, cấu hình cổng TCP, hiện IP LAN và thời gian hệ thống.
- Thống kê tài khoản, client online, tổng lượt Join hiện tại; các bản tin Unicast/Group/Broadcast được ghi trong nhật ký.
- Bảng phiên: tài khoản, IP/cổng client, giờ đăng nhập/đăng xuất, Online/Offline, nhóm và mã phiên (hiện 8 ký tự đầu, tooltip chứa mã đầy đủ).
- Ngắt một phiên đã chọn. Lịch sử tối đa 1.000 phiên trong bộ nhớ; chi tiết login/logout còn trong file log.
- Nút **Nhóm** mở bảng phòng/nhóm và danh sách người nhận hiện tại.
- Log thời gian/sự kiện/thông tin, tìm kiếm, lọc theo sự kiện, xuất 500 dòng gần nhất. File log hàng ngày lưu toàn bộ sự kiện.
- Dừng server đóng cả socket đang đăng nhập và chưa đăng nhập, xóa membership; khởi động lại giữ tài khoản.

## Quy tắc gửi đúng theo đề

| Chế độ | Client được nhận bản tin mạng |
|---|---|
| Unicast | Chỉ tài khoản người nhận online |
| Group (multicast ở tầng ứng dụng) | Chỉ thành viên hiện tại của nhóm đích |
| Broadcast | Tất cả client đã đăng nhập, kể cả người gửi |

**Ví dụ Leave trong đề:** client 1 Leave rồi vẫn có thể gửi vào nhóm; client 2 và 3 còn Join nhận tin, client 1 không nhận bản tin nhóm. Join/Leave kiểm soát **quyền nhận**, không chặn gửi vào nhóm. UI người gửi hiển thị bản sao **ĐÃ GỬI** khi có ACK; đây không phải bản tin Group được server gửi lại cho người ngoài nhóm. Tương tự, Unicast có ACK cho người gửi nhưng chỉ người nhận nhận bản tin `UNICAST`.

Server gửi ACK khi đã xếp bản tin vào các hàng đợi gửi của các client đích. ACK không phải xác nhận người nhận đã đọc. Log ghi rõ danh sách client được định tuyến.

Group và Broadcast được server phân phối qua từng kết nối **TCP**, không sử dụng IP multicast hoặc UDP broadcast. Đây là mô phỏng các chế độ truyền tin ở tầng ứng dụng theo mô hình client–server.

## Chạy nhanh

Yêu cầu **JDK 17 trở lên** (Java 21 cũng chạy được). Maven 3.9+ để build mã nguồn.

```bash
java -version
mvn -version
mvn clean install
```

Sau build:

```bash
java -jar server/target/TCPGroupChat-server.jar
java -jar client/target/TCPGroupChat-client.jar
```

Trên Windows: chạy `build.bat`, sau đó `run-server.bat` và mở `run-client.bat` nhiều lần. Nếu bản đóng gói có thư mục `dist`, hai file chạy sẽ tự dùng JAR trong `dist`, chỉ cần Java, không cần build lại.

Trong IntelliJ: mở thư mục gốc dưới dạng Maven project, chọn JDK 17 và chạy:

- Server: `com.tcpgroupchat.server.ChatServer` → nhấn **Khởi động**.
- Client: `com.tcpgroupchat.client.ClientApplication` → đăng ký tài khoản, đăng nhập.
- Bật **Allow multiple instances** trong Run Configuration của client để mở 5 cửa sổ.

Cũng có thể chạy sau `mvn clean install`:

```bash
mvn -pl server exec:java -Dexec.mainClass=com.tcpgroupchat.server.ChatServer
mvn -pl client exec:java -Dexec.mainClass=com.tcpgroupchat.client.ClientApplication
```

## Kịch bản demo 5 client

1. Khởi động server ở cổng `2006`.
2. Đăng ký `client1` đến `client5`, đăng nhập bằng 5 cửa sổ khác nhau.
3. Client 1, 2, 3 chọn nhóm `study` rồi **Join**; client 4, 5 ở ngoài. Dashboard hiện nhóm có 3 thành viên.
4. Client 1 chọn **Group** và gửi `Tin nhóm lần 1`: chỉ client 1, 2, 3 nhận bản tin nhóm. Xem log server để đối chiếu danh sách nhận.
5. Client 1 **Leave**, sau đó chính client 1 gửi `Tin sau Leave`: chỉ client 2, 3 nhận bản tin nhóm. Client 1 thấy **ĐÃ GỬI** và ACK cho 2 đích; client 4, 5 không nhận.
6. Client 1 chọn **Unicast**, người nhận `client4`, gửi `Tin riêng`: chỉ client 4 nhận `UNICAST`; client 1 có bản sao gửi và ACK.
7. Client 5 chọn **Broadcast** và gửi `Tin toàn hệ thống`: cả 5 nhận, không phụ thuộc nhóm.
8. Đóng client 2: số online và danh sách nhóm cập nhật, phiên chuyển Offline có giờ kết thúc.
9. Dừng server: các client mất kết nối và không gửi được. Khởi động lại, đăng nhập lại; tài khoản còn, nhóm cần Join lại.

## Chạy trên hai máy LAN

Server chạy trên máy A; client chạy máy B. Tại màn hình đăng nhập máy B, điền IP LAN máy A (ví dụ `192.168.1.10`) và cổng server. Cho phép **TCP** cổng đó trên firewall máy A. Không dùng `127.0.0.1` để nối sang máy khác.

Giá trị mặc định có thể cấu hình bằng `CHAT_HOST`, `CHAT_PORT` hoặc Java properties `chat.host`, `chat.port`:

```bash
java -Dchat.host=192.168.1.10 -Dchat.port=2006 -jar client/target/TCPGroupChat-client.jar
java -Dchat.port=2006 -Dchat.data=server_data -jar server/target/TCPGroupChat-server.jar
```

## Cấu trúc và dữ liệu

```text
common/   giao thức ChatMessage, JsonLines, style và icon dùng chung
server/   ChatServer, ServerFrame, service/AccountStore, test socket thật
client/   ClientApplication, LoginFrame, ChatFrame, ClientConnection
server_data/
  accounts/<username>.properties   salt + PBKDF2 hash, IP và giờ đăng ký
  logs/YYYY-MM-DD.log              log hoạt động hàng ngày
```

`server_data` được tạo khi chạy, không commit vào git. Mặc định đường dẫn tính từ thư mục chạy server; nên chạy từ thư mục gốc để dùng cùng dữ liệu. Đổi bằng `-Dchat.data=...` nếu cần.

Mật khẩu lưu bằng PBKDF2-HMAC-SHA256 (120.000 vòng, salt riêng từng tài khoản); không ghi mật khẩu vào log. Phiên, membership và thống kê bản tin giữ trong bộ nhớ, mất khi thoát chương trình server. Tài khoản và file log tồn tại sau khi mở lại. Không lưu lịch sử chat offline cho client; bản demo dùng TCP chưa mã hóa TLS.

## Giao thức TCP

Mỗi dòng UTF-8 là một JSON. TCP là luồng byte, nên phía nhận ghép frame đến ký tự xuống dòng; hỗ trợ cả frame bị chia nhỏ và nhiều frame nhận liên tiếp. JSON tự escape ký tự xuống dòng trong nội dung.

Các trường chính: `type`, `requestId`, `sender`, `recipient`, `group`, `content`; xác thực dùng thêm `password`. Server trả `sessionId`, `timestamp`, `users`, `members`, `joinedGroups` theo loại phản hồi. Server lấy danh tính từ phiên socket, không tin `sender` của yêu cầu chat.

| Yêu cầu | Phản hồi / sự kiện |
|---|---|
| REGISTER | REGISTER_OK / ERROR |
| LOGIN | LOGIN_OK / ERROR; USER_LIST, GROUP_STATE |
| JOIN_GROUP | JOIN_OK / ERROR; GROUP_STATE |
| LEAVE_GROUP | LEAVE_OK / ERROR; GROUP_STATE |
| GET_GROUP | GROUP_STATE |
| GET_USERS | USER_LIST |
| UNICAST / GROUP_MESSAGE / BROADCAST | Bản tin tới đích và ACK tới người gửi; hoặc ERROR |
| LOGOUT | Server đóng kết nối và dọn phiên |
| PING | PONG |

Giới hạn: 100 kết nối, 100 nhóm, nội dung 4.000 ký tự, frame 32.768 ký tự, hàng đợi gửi mỗi client 256 bản tin. Kết nối chưa đăng nhập chờ tối đa 60 giây. Client quá chậm đầy hàng đợi bị ngắt để không chặn các client khác.

Membership và định tuyến xử lý dưới cùng khóa trạng thái, nên Leave thành công sẽ có hiệu lực cho các tin được xử lý tiếp theo. Tin đã định tuyến trước Leave có thể còn trong hàng đợi TCP. Mỗi socket có reader và writer riêng; giao diện Swing cập nhật trên EDT, kết nối/gửi và đọc dữ liệu dashboard chạy ngoài EDT.

## Kiểm thử

```bash
mvn test
```

Kiểm thử bằng socket TCP thật, gồm 5 client nhận đúng theo từng chế độ, gửi sau Leave, nhiều phòng, Join lặp, xác thực, đăng nhập trùng, giả mạo sender, input lỗi, ngắt kết nối/đăng nhập lại, stop/restart, chia/gộp frame, frame quá lớn, ngắt phiên và 100 bản tin đồng thời.
