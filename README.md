# OISHI FOOD - HỆ SINH THÁI ĐẶT & GIAO ĐỒ ĂN ĐA PHÂN HỆ
> **Học phần:** Chuyên đề Tốt nghiệp 2  
> **Đơn vị:** Trường Cao đẳng Công nghệ Thủ Đức (TDC) – Khoa Công nghệ Thông tin  
> **Năm học:** 2026  
> **Nhóm thực hiện:** Lê Tấn Giàu (33%), Trần Vũ (33%), Dương Minh Thư (33%), Hồ Thị Vân Anh (1%)

---

## 1. TỔNG QUAN HỆ THỐNG
Hệ thống **Oishi Food** là giải pháp số hóa toàn diện quy trình cung ứng thực phẩm và dịch vụ ăn uống (F&B) được xây dựng theo kiến trúc **Android Multi-Module Clean Architecture** hiện đại, kết nối thời gian thực qua nền tảng Google Firebase Realtime Database.

Hệ thống bao gồm **3 ứng dụng Android Native hoàn chỉnh và 1 module thư viện dùng chung**:
1. **`:app-customer`**: Ứng dụng Android dành riêng cho Khách hàng (Duyệt quán, chọn combo/topping, giỏ hàng, thanh toán VietQR Napas247 tự động, theo dõi GPS tài xế, mã QR/PIN nhận hàng 2 chiều, khiếu nại).
2. **`:app-internal`**: Ứng dụng Android Nội bộ phục vụ 4 vai trò vận hành:
   - **Chủ chuỗi (`BOSSMERCHANT`):** Quản lý chuỗi nhà hàng, mở chi nhánh mới, phân quyền cho Quản lý cửa hàng, xem báo cáo doanh thu toàn chuỗi.
   - **Quản lý quán (`MERCHANT`):** Cấu hình ngân hàng VietQR của quán, quản lý thực đơn món ăn/combo/topping, tiếp nhận đơn hàng Kanban 4 tab, điều phối tài xế trực thuộc.
   - **Đầu bếp (`CHEF`):** Tiếp nhận danh sách món cần nấu (`PREPARING`) với cơ chế khóa nguyên tử (Atomic Lock), cập nhật hoàn tất chuyển trạng thái `READY`.
   - **Tài xế giao hàng (`DRIVER` / `SHIPPER`):** Nhận đơn sẵn sàng, ghép chuyến đa điểm (tối đa 3 đơn cùng tuyến), định vị lộ trình Google Maps, quét Camera QR / PIN giao hàng, xử lý sự cố hoàn trả món về quán (`RETURNING_TO_STORE`).
3. **`:app-admin`**: Ứng dụng Android Native dành cho Quản trị viên sàn (5 tab chuyên sâu: Dashboard KPI, Quản lý & kiểm duyệt Cửa hàng, Quản lý Danh mục món toàn sàn, Phán quyết Tranh chấp khiếu nại & can thiệp uy tín, Giám sát Realtime đơn hàng và người dùng).
4. **`:core`**: Thư viện dùng chung chứa 26 Data Models, `FirebaseRepository` (178 hàm), `AuthRepository` (20 hàm), các Services ngầm (`BankNotificationListenerService`, `LocationForegroundService`, `MerchantKeepAliveService`) và bộ Utilities hỗ trợ.

---

## 2. MA TRẬN PHÂN QUYỀN 6 ROLES (RBAC MATRIX)

| STT | Mã Role | Tên Tác nhân | Phân hệ Ứng dụng | Trách nhiệm cốt lõi |
|:---:|:---|:---|:---|:---|
| 1 | `CUSTOMER` | Khách hàng | `:app-customer` | Đăng nhập OTP, đặt món/combo, thanh toán VietQR/COD, theo dõi GPS, xác nhận QR nhận món, đánh giá sao, tích lũy điểm uy tín. |
| 2 | `BOSSMERCHANT` | Chủ chuỗi (Boss) | `:app-internal` | Quản lý chuỗi F&B, tạo chi nhánh (`stores`), phân quyền Quản lý chi nhánh (`Merchant`), xem tổng quan doanh thu toàn chuỗi. |
| 3 | `MERCHANT` | Quản lý quán | `:app-internal` | Thiết lập thông tin quán, cấu hình VietQR ngân hàng, quản lý menu món/combo, tiếp nhận đơn Kanban 4 tab, quản lý shipper quán. |
| 4 | `CHEF` | Đầu bếp | `:app-internal` | Nhận đơn cần nấu (`PREPARING`) với Atomic Lock, xem định lượng/ghi chú, bấm 'Đã nấu xong' chuyển đơn sang `READY`. |
| 5 | `DRIVER` | Tài xế (Shipper) | `:app-internal` | Nhận đơn `READY`, ghép đơn đa điểm (tối đa 3 đơn), Google Maps, quét QR/PIN giao khách, báo sự cố hoàn trả quán `RETURNING_TO_STORE`. |
| 6 | `ADMIN` | Quản trị viên sàn | `:app-admin` | Kiểm duyệt quán mới, quản lý danh mục toàn sàn, giám sát realtime đơn hàng & user, phán quyết khiếu nại, cấu hình sàn. |

---

## 3. CÁC TÍNH NĂNG CÔNG NGHỆ ĐỘT PHÁ

### 3.1 Cơ chế Chống bùng hàng & Điểm Uy Tín 5 Bậc (`Customer.kt`)
- **5 Phân hạng uy tín:**
  - 💎 **Kim Cương ($\ge 95$ điểm):** Được COD, ưu đãi giảm 5.000đ phí vận chuyển.
  - 🥇 **Vàng ($\ge 90$ điểm):** Được thanh toán tiền mặt COD bình thường.
  - 🥈 **Bạc ($\ge 50$ điểm):** **CẤM COD**, bắt buộc Đặt cọc 20% qua VietQR hoặc thanh toán trước 100%.
  - ⚠️ **Hạn Chế ($\ge 20$ điểm):** Bắt buộc đặt cọc 20%, giám sát rủi ro đặc biệt.
  - 🚫 **Đình Chỉ ($< 20$ điểm):** Khóa quyền đặt hàng.
- **Quy trình Hoàn hàng `RETURNING_TO_STORE`:**
  - Khi Shipper đến điểm giao nhưng không liên lạc được khách quá 15 phút, Shipper chụp ảnh màn hình cuộc gọi làm bằng chứng (`incidentCallProofImage`) và kích hoạt báo sự cố.
  - Ứng dụng Shipper khóa chặt giao diện, điều hướng bắt buộc mang món về hoàn trả cho Quán. Quán nhận lại món, bấm xác nhận hoàn tất; hệ thống tự động trừ nặng điểm uy tín của khách và bảo toàn công vận chuyển cho tài xế.

### 3.2 Tự Động Hóa Thanh Toán VietQR Napas247
- Tự động sinh mã VietQR chuẩn ngân hàng chứa cú pháp mã đơn duy nhất.
- Dịch vụ ngầm `BankNotificationListenerService` chạy trên thiết bị quán, bắt thông báo biến động số dư ngân hàng và phân tích regex, tự động duyệt đơn sang trạng thái `PAID` và `PREPARING` trong vòng 1-2 giây mà không cần kiểm tra thủ công.

### 3.3 Chống Tranh Chấp Đơn (Firebase Transaction Lock)
- Bếp (`assignChef` / `unassignChef`) và Tài xế (`assignShipper` / `unassignShipper`) sử dụng `Firebase Database Handler Transaction` đảm bảo không bao giờ xảy ra tình trạng 2 đầu bếp hoặc 2 tài xế nhận trùng cùng một đơn hàng.

---

## 4. TÀI LIỆU BÁO CÁO DỰ ÁN
- **Báo cáo tốt nghiệp Word chuẩn TDC (65 trang đầy đủ):** [BAO_CAO_TOT_NGHIEP_OISHI_FOOD_CHUAN_TDC.docx](BAO_CAO_TOT_NGHIEP_OISHI_FOOD_CHUAN_TDC.docx)
- **Báo cáo kiến trúc hệ thống chi tiết:** [BAO_CAO_KIEN_TRUC_HE_THONG_MOI_OISHI_FOOD.md](BAO_CAO_KIEN_TRUC_HE_THONG_MOI_OISHI_FOOD.md)
- **Bộ dữ liệu mẫu kiểm thử toàn sàn:** [scripts/seed_sample_data.py](scripts/seed_sample_data.py)
