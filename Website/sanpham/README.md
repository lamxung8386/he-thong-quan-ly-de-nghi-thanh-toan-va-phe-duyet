# Hệ thống quản lý đề nghị thanh toán và phê duyệt

**Bản giao diện trắng – xanh lá, tháng 10/2026.** Gồm 15 màn hình, sử dụng Java Spring Boot 3.3.5, Thymeleaf, Spring Security và PostgreSQL 16. Đây là hệ thống mô phỏng nghiệp vụ nội bộ dùng cho học tập; **không chuyển tiền thật**. Chữ *Grab* trong mẫu chỉ là nhận diện minh họa theo yêu cầu thiết kế, không phải phần mềm chính thức của Grab.

## Cài đặt và chạy trên Windows, VS Code

Yêu cầu JDK 17+, Maven, Docker Desktop/WSL2. Mở Terminal ở thư mục `FIN01_FULL` có `pom.xml` và `docker-compose.yml`.

```powershell
# Kiểm tra các công cụ
java -version
mvn -version
docker compose version

# Mở Docker Desktop trước, sau đó khởi động PostgreSQL
docker compose up -d
docker compose ps
docker compose exec db pg_isready -U fin01 -d fin01

# Kiểm tra mã và chạy website
mvn clean test
mvn spring-boot:run
```

Truy cập **http://localhost:8080**. Múi giờ Việt Nam đã được cấu hình trong `Fin01Application.java`, không cần nhập thủ công `$env:JAVA_TOOL_OPTIONS` (nếu Terminal có giá trị môi trường cũ, ứng dụng vẫn đặt lại múi giờ tại khởi động).

### Tài khoản mẫu (chỉ dùng trên localhost)

| Tài khoản | Vai trò | Mật khẩu |
|---|---|---|
| nguoilap | Nhân viên lập đề nghị | 123456 |
| nguoiduyet | Người phê duyệt | 123456 |
| ketoan | Kế toán | 123456 |
| admin | Quản trị viên | 123456 |
| kiemtoan | Kiểm toán viên | 123456 |

Đổi mật khẩu trước khi dùng ngoài môi trường demo. Cơ chế đăng ký tạo tài khoản `USER` và mật khẩu được băm với BCrypt.

## Các trang đã nối với dữ liệu thật

- Trang chủ `/` có lời giới thiệu và công việc theo vai trò, thống kê lấy từ PostgreSQL.
- Danh sách `/expenses`, tìm kiếm theo mã, tên, nội dung và trạng thái.
- Tạo đề nghị `/expenses/new` nhập số tiền, hóa đơn, người nhận và tải chứng từ ngay trong form; tạo bản nháp trong database.
- Chi tiết `/expenses/{id}` xem hồ sơ, chứng từ, lịch sử, phê duyệt, chi trả và đối soát; các nút thao tác phụ thuộc quyền và trạng thái.
- Phê duyệt `/approvals` cho người duyệt; người lập không được tự duyệt hồ sơ của mình.
- Ghi nhận chi trả `/payments` cho kế toán: tên người nhận phải khớp hồ sơ; số tiền không vượt còn phải chi; chi một phần được bổ sung về sau; tiền mặt không lưu tài khoản ngân hàng; chuyển khoản nhập tài khoản ảo, không có tích hợp ngân hàng.
- Đối soát `/reconciliation`: không hoàn tất nếu chưa ghi nhận đủ số tiền và người nhận hợp lệ.
- Báo cáo `/reports`, thông báo `/notifications`, hồ sơ `/profile`, quản trị `/admin`, nhật ký `/audit`.
- Hồ sơ cá nhân: sửa họ tên, email, mật khẩu và **tải ảnh đại diện** PNG/JPG tối đa 2 MB. File được lưu ở `uploads/avatars`; tên file liên kết qua `app_users.avatar_file` trong PostgreSQL.

## Cơ sở dữ liệu

Docker sử dụng database `fin01`, tài khoản `fin01`, mật khẩu `fin01_dev`, cổng `5432`. `src/main/resources/schema.sql` tự tạo bảng chưa có và thêm cột `avatar_file` nếu database cũ chưa có. Không xóa dữ liệu cũ và không chạy lệnh `docker compose down -v` nếu muốn giữ các hồ sơ.

Để thầy kiểm tra trực tiếp:

```powershell
docker compose exec db psql -U fin01 -d fin01 -c "\dt"
docker compose exec db psql -U fin01 -d fin01 -c "SELECT id,code,amount,status FROM expenses ORDER BY id DESC LIMIT 10;"
docker compose exec db psql -U fin01 -d fin01 -c "SELECT id,username,role,avatar_file FROM app_users ORDER BY id;"
docker compose exec db psql -U fin01 -d fin01 -c "SELECT id,expense_id,paid_amount,paid_payee,payment_method,status FROM payments ORDER BY id DESC LIMIT 10;"
```

Tạo một đề nghị bằng web, chạy câu `SELECT` lần nữa để thấy dữ liệu mới. Dữ liệu tồn tại qua việc tắt, mở lại Spring Boot vì nằm trong Docker volume `fin01_pgdata`. Nếu máy đã có PostgreSQL khác dùng cổng `5432`, cần xử lý xung đột trước khi chạy.

## Kiểm tra nghiệp vụ thực tế

1. Đăng nhập `nguoilap`, tạo đề nghị 10.000.000đ và tải chứng từ PDF/JPG/PNG. Kiểm tra hồ sơ mới ở trạng thái bản nháp, sau đó gửi duyệt.
2. Đăng nhập `nguoiduyet` và phê duyệt. Thử một tài khoản người lập tự duyệt: phải bị chặn.
3. Đăng nhập `ketoan`. Nhập sai tên người nhận: báo lỗi. Nhập 11.000.000đ: báo lỗi. Nhập 9.000.000đ: chuyển đã chi một phần. Bổ sung thêm 1.000.000đ: chờ đối soát.
4. Chuyển khoản: yêu cầu tên ngân hàng và tài khoản ảo 6–20 chữ số. Tiền mặt: không yêu cầu và không lưu ngân hàng.
5. Đối soát chưa đủ tiền: không hoàn tất. Đối soát đủ 10.000.000đ, đúng người nhận: hoàn tất. Tất cả là dữ liệu mô phỏng.
6. Đổi ảnh đại diện, đăng xuất, đăng nhập lại; ảnh vẫn hiện vì liên kết file lưu trong cơ sở dữ liệu.
7. Đăng nhập `admin`, kiểm tra quyền và hạn mức; đăng nhập `kiemtoan` xem nhật ký.

Các bài JUnit nằm tại `src/test/java/vn/fin01/` với các tình huống nghiệp vụ và avatar. 

