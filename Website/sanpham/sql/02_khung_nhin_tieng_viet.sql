-- Khung nhìn tiếng Việt phục vụ trình bày và tra cứu CSDL FIN-01.
-- An toàn: chỉ tạo VIEW, không xóa, đổi tên hay cập nhật bảng/dữ liệu gốc.
-- Lưu ý: VIEW dùng để xem; chương trình vẫn đọc ghi vào bảng gốc tiếng Anh.

CREATE OR REPLACE VIEW phong_ban AS
SELECT id AS ma_phong_ban, name AS ten_phong_ban FROM departments;

CREATE OR REPLACE VIEW loai_chi_phi AS
SELECT id AS ma_loai_chi, name AS ten_loai_chi FROM categories;

CREATE OR REPLACE VIEW nguoi_dung AS
SELECT u.id AS ma_nguoi_dung, u.username AS ten_dang_nhap,
 u.full_name AS ho_ten, u.email AS thu_dien_tu,
 CASE u.role
  WHEN 'USER' THEN 'Nhân viên lập đề nghị'
  WHEN 'APPROVER' THEN 'Người phê duyệt'
  WHEN 'ACCOUNTANT' THEN 'Nhân viên kế toán'
  WHEN 'ADMIN' THEN 'Quản trị viên'
  WHEN 'AUDITOR' THEN 'Kiểm toán viên'
  ELSE u.role END AS vai_tro,
 d.name AS phong_ban, u.limit_amount AS han_muc,
 CASE WHEN u.active THEN 'Đang hoạt động' ELSE 'Đã khóa' END AS trang_thai,
 u.created_at AS ngay_tao
 FROM app_users u LEFT JOIN departments d ON d.id=u.department_id;

CREATE OR REPLACE VIEW de_nghi_thanh_toan AS
SELECT e.id AS ma_ho_so, e.code AS so_de_nghi,
 u.full_name AS nguoi_lap, c.name AS loai_chi_phi,
 e.purpose AS noi_dung, e.payee AS nguoi_nhan,
 e.amount AS so_tien_duoc_de_nghi,
 e.supplier AS nha_cung_cap, e.invoice_no AS so_hoa_don,
 e.invoice_date AS ngay_hoa_don,
 CASE e.status
  WHEN 'DRAFT' THEN 'Bản nháp'
  WHEN 'SUBMITTED' THEN 'Chờ phê duyệt'
  WHEN 'APPROVED' THEN 'Đã phê duyệt'
  WHEN 'REJECTED' THEN 'Đã từ chối'
  WHEN 'PAYMENT_FAILED' THEN 'Chi trả thất bại'
  WHEN 'PARTIALLY_PAID' THEN 'Đã chi một phần'
  WHEN 'PAID' THEN 'Đã chi đủ – Chờ đối soát'
  WHEN 'NEEDS_REVIEW' THEN 'Có chênh lệch – Cần xử lý'
  WHEN 'COMPLETED' THEN 'Đã hoàn tất'
  ELSE e.status END AS trang_thai,
 e.created_at AS ngay_tao, e.updated_at AS ngay_cap_nhat
 FROM expenses e JOIN app_users u ON e.created_by=u.id
 JOIN categories c ON e.category_id=c.id;

CREATE OR REPLACE VIEW chung_tu AS
SELECT d.id AS ma_chung_tu, e.code AS so_de_nghi,
 d.original_name AS ten_tep_goc,
 d.content_type AS loai_tep, d.size_bytes AS dung_luong_byte,
 d.uploaded_at AS ngay_tai_len
 FROM documents d JOIN expenses e ON e.id=d.expense_id;

CREATE OR REPLACE VIEW phe_duyet AS
SELECT a.id AS ma_phan_hoi, e.code AS so_de_nghi,
 u.full_name AS nguoi_phe_duyet,
 CASE a.decision WHEN 'APPROVED' THEN 'Đã phê duyệt'
 WHEN 'REJECTED' THEN 'Đã từ chối' ELSE a.decision END AS quyet_dinh,
 a.reason AS ly_do, a.decided_at AS ngay_xu_ly
 FROM approvals a JOIN expenses e ON e.id=a.expense_id
 JOIN app_users u ON u.id=a.approver_id;

CREATE OR REPLACE VIEW phieu_chi AS
SELECT p.id AS ma_phieu_chi, e.code AS so_de_nghi,
 p.reference_code AS ma_giao_dich_mo_phong,
 u.full_name AS ke_toan_thuc_hien,
 p.paid_amount AS so_tien_da_ghi_nhan,
 p.paid_payee AS nguoi_nhan_thuc_te,
 CASE p.payment_method WHEN 'TRANSFER' THEN 'Chuyển khoản ngân hàng ảo'
 WHEN 'CASH' THEN 'Tiền mặt' ELSE 'Chưa khai báo' END AS phuong_thuc,
 p.bank_name AS ngan_hang_ao, p.bank_account AS so_tai_khoan_ao,
 CASE p.status WHEN 'SUCCESS' THEN 'Ghi nhận thành công'
 WHEN 'FAILED' THEN 'Ghi nhận thất bại' ELSE p.status END AS ket_qua,
 CASE WHEN p.voided THEN 'Đã hủy' ELSE 'Có hiệu lực' END AS hieu_luc,
 p.void_reason AS ly_do_huy, p.paid_at AS ngay_ghi_nhan
 FROM payments p JOIN expenses e ON e.id=p.expense_id
 JOIN app_users u ON u.id=p.payer_id;

CREATE OR REPLACE VIEW doi_soat AS
SELECT r.id AS ma_doi_soat, e.code AS so_de_nghi,
 p.reference_code AS ma_giao_dich_mo_phong,
 u.full_name AS nguoi_doi_soat,
 CASE r.result WHEN 'MATCHED' THEN 'Khớp số liệu'
 WHEN 'MISMATCHED' THEN 'Có chênh lệch' ELSE r.result END AS ket_qua,
 r.note AS ghi_chu, r.created_at AS ngay_doi_soat
 FROM reconciliations r JOIN payments p ON p.id=r.payment_id
 JOIN expenses e ON e.id=r.expense_id
 JOIN app_users u ON u.id=r.reconciled_by;

CREATE OR REPLACE VIEW thong_bao AS
SELECT n.id AS ma_thong_bao, u.full_name AS nguoi_nhan,
 n.message AS noi_dung, n.created_at AS ngay_tao,
 CASE WHEN n.read_at IS NULL THEN 'Chưa đọc' ELSE 'Đã đọc' END AS trang_thai
 FROM notifications n JOIN app_users u ON u.id=n.recipient_id;

CREATE OR REPLACE VIEW nhat_ky_he_thong AS
SELECT l.id AS ma_nhat_ky, COALESCE(u.full_name,'Hệ thống') AS nguoi_thuc_hien,
 CASE l.entity_type WHEN 'EXPENSE' THEN 'Đề nghị thanh toán'
 WHEN 'USER' THEN 'Người dùng'
 WHEN 'CATEGORY' THEN 'Loại chi phí'
 WHEN 'DEPARTMENT' THEN 'Phòng ban' ELSE l.entity_type END AS loai_doi_tuong,
 l.entity_id AS ma_doi_tuong,
 CASE l.action
 WHEN 'CREATE' THEN 'Tạo mới' WHEN 'EDIT' THEN 'Chỉnh sửa'
 WHEN 'SUBMIT' THEN 'Gửi phê duyệt'
 WHEN 'APPROVED' THEN 'Phê duyệt' WHEN 'REJECTED' THEN 'Từ chối'
 WHEN 'PAY_SUCCESS' THEN 'Chi trả đủ' WHEN 'PAY_PARTIAL' THEN 'Chi trả một phần'
 WHEN 'PAY_FAILED' THEN 'Chi trả thất bại'
 WHEN 'VOID_PAYMENT' THEN 'Hủy phiếu chi sai'
 WHEN 'SUPPLEMENT_PAYMENT' THEN 'Bổ sung thông tin chi trả'
 WHEN 'RECONCILE_OK' THEN 'Đối soát khớp'
 WHEN 'RECONCILE_DIFF' THEN 'Đối soát chênh lệch'
 WHEN 'UPLOAD' THEN 'Tải chứng từ lên'
 WHEN 'PROFILE' THEN 'Cập nhật hồ sơ cá nhân'
 WHEN 'AVATAR' THEN 'Đổi ảnh đại diện'
 WHEN 'PASSWORD' THEN 'Đổi mật khẩu'
 WHEN 'ADMIN_UPDATE' THEN 'Cập nhật phân quyền'
 ELSE l.action END AS thao_tac,
 l.detail AS chi_tiet, l.created_at AS ngay_thuc_hien
 FROM audit_logs l LEFT JOIN app_users u ON u.id=l.actor_id;
