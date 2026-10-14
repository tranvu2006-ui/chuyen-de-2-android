# BẢNG PHÂN CÔNG CÔNG VIỆC DỰ ÁN (TRELLO WORK BREAKDOWN)
> **ĐỒ ÁN CHUYÊN ĐỀ 2 • HỆ THỐNG ĐẶT & GIAO MÓN TRỰC TUYẾN OISHI FOOD**  
> **Kiến trúc:** Multi-module Android (`core`, `app-customer`, `app-internal`) + Web Admin + Firebase Realtime Database  
> **Quy chuẩn quản lý:** Agile / Scrum Board trên Trello

---

## 1. CẤU TRÚC DANH SÁCH CỘT (LISTS) TRÊN TRELLO
Board Trello của nhóm được thiết lập theo quy trình 5 bước:
1. 📋 **Backlog:** Danh sách toàn bộ tính năng và nhiệm vụ cần làm của đề tài.
2. 🎯 **Sprint To Do:** Các task được chọn để thực hiện trong tuần / Sprint hiện tại.
3. ⚙️ **In Progress:** Các công việc đang được code và triển khai.
4. 🧪 **Review & Testing:** Giai đoạn kiểm thử trên máy ảo/máy thật và review code chéo.
5. ✅ **Done:** Task đã hoàn thành, vượt qua kiểm thử và được merge vào nhánh chính (`main`).

---

## 2. HỆ THỐNG NHÃN DÁN (LABELS & TAGS) TRÊN TRELLO
| Tên Nhãn (Label) | Màu Sắc | Ý Nghĩa / Phạm Vi Áp Dụng |
| :--- | :--- | :--- |
| **Module Core** | 🟣 Tím | Kiến trúc nền tảng, Firebase CSDL, Auth, Dịch vụ GPS |
| **App Customer** | 🟢 Xanh lá | Ứng dụng Khách hàng (`HomeActivity`, Giỏ hàng, Đặt món, Tracking) |
| **App Merchant** | 🟠 Cam | Ứng dụng Quán ăn (Kanban đơn, Thực đơn, Quản lý tài xế) |
| **App Shipper** | 🔵 Xanh dương | Ứng dụng Tài xế (Nhận đơn, Ghép đơn, Lộ trình 4 chặng, Ví tiền) |
| **Web Admin** | 🔴 Đỏ | Bảng điều khiển Quản trị viên (`admin.html`), Duyệt quán KYC |
| **High / Urgent** | ⚠️ Vàng | Nhiệm vụ ưu tiên cao, ảnh hưởng luồng chính của đồ án |

---

## 3. MA TRẬN PHÂN VAI THÀNH VIÊN (TEAM ROLES)

### Phương án nhóm 4 thành viên (Khuyến nghị chuẩn):
- **Thành viên A (Trưởng nhóm / Backend & Core):** Kiến trúc Multi-module, Thiết kế CSDL Firebase, Authentication & RBAC, GPS Service, Map API.
- **Thành viên B (Frontend Mobile - Customer):** Toàn bộ giao diện và logic đặt món dành cho Khách hàng (`app-customer`).
- **Thành viên C (Frontend Mobile - Merchant):** Toàn bộ giao diện và chức năng dành cho Quán ăn / Chủ cửa hàng (`app-internal`).
- **Thành viên D (Frontend Mobile - Shipper & Web Admin):** Giao diện Tài xế giao hàng (`app-internal`), Lộ trình 4 chặng, Ví thu nhập & Web Admin (`admin.html`).

*(Ghi chú: Nếu nhóm có 3 thành viên, Thành viên C sẽ phụ trách toàn bộ module `app-internal` bao gồm cả Quán ăn và Shipper).*

---

## 4. CHI TIẾT TỪNG THẺ CÔNG VIỆC TRÊN TRELLO (TRELLO CARDS)

### EPICS 1: NỀN TẢNG KIẾN TRÚC & CORE CHUNG (`core`)
**Phụ trách chính:** Thành viên A

#### Thẻ 1.1: [CORE] Thiết lập Kiến trúc Multi-Module Android
- **Nhãn:** `Module Core`, `High / Urgent`
- **Mô tả:** Khởi tạo kiến trúc 3 module độc lập giúp tối ưu biên dịch và tách biệt trách nhiệm logic.
- **Checklist công việc:**
  - [ ] Khởi tạo module `core`, `app-customer`, `app-internal`.
  - [ ] Cấu hình Gradle chung: compileSdk 35, Java 17, ViewBinding, Hilt Dependency Injection.
  - [ ] Tạo bảng màu nhận diện thương hiệu `colors.xml` và theme Material 3.

#### Thẻ 1.2: [CORE] Thiết kế & Cấu hình CSDL Firebase Realtime Database
- **Nhãn:** `Module Core`, `High / Urgent`
- **Mô tả:** Xây dựng cấu trúc cây JSON dữ liệu thời gian thực cho toàn bộ 4 Actors.
- **Checklist công việc:**
  - [ ] Thiết kế cấu trúc các node: `orders`, `users`, `stores`, `dishes`, `tracking`.
  - [ ] Viết bộ quy tắc bảo mật `database.rules.json` theo từng vai trò.
  - [ ] Nạp dữ liệu khởi tạo ban đầu (`firebase_database.json`).

#### Thẻ 1.3: [CORE] Xác thực Firebase Auth & Điều phối Phân quyền (RBAC)
- **Nhãn:** `Module Core`, `High / Urgent`
- **Mô tả:** Đăng ký, đăng nhập và phân luồng tự động 4 vai trò.
- **Checklist công việc:**
  - [ ] Xây dựng `AuthRepository` xử lý đăng ký, đăng nhập bằng Email/Password.
  - [ ] Lưu trữ và truy xuất vai trò người dùng: `CUSTOMER`, `MERCHANT`, `SHIPPER`, `ADMIN`.
  - [ ] Điều hướng tự động: Khách vào `app-customer`, Quán/Tài xế vào `app-internal`.

#### Thẻ 1.4: [CORE] Dịch vụ Định vị Nền GPS & Google Maps Turn-by-Turn
- **Nhãn:** `Module Core`
- **Mô tả:** Cập nhật vị trí tài xế theo thời gian thực và công cụ dẫn đường.
- **Checklist công việc:**
  - [ ] Viết `LocationForegroundService` chạy ngầm cập nhật GPS lên node `tracking/{orderId}`.
  - [ ] Viết `GoogleMapUtils` tính khoảng cách đường bộ và mở điều hướng Google Maps.
  - [ ] Xây dựng Firebase Transaction Lock gán đơn chống xung đột 2 quản lý cùng bấm.

---

### EPICS 2: ỨNG DỤNG KHÁCH HÀNG (`app-customer`)
**Phụ trách chính:** Thành viên B

#### Thẻ 2.1: [CUST] Màn hình Trang Chủ & Món Hot Bán Chạy (`HomeActivity`)
- **Nhãn:** `App Customer`, `High / Urgent`
- **Mô tả:** Giao diện lôi cuốn khám phá ẩm thực theo thiết kế `khách/a1.html`.
- **Checklist công việc:**
  - [ ] Thanh tìm kiếm thông minh, banner ưu đãi động.
  - [ ] RecyclerView danh sách cửa hàng đối tác (kèm đánh giá sao, thời gian giao).
  - [ ] Danh sách món hot bán chạy, bấm chọn tự động cộng số lượng vào giỏ.

#### Thẻ 2.2: [CUST] Chi tiết Cửa Hàng & Thực Đơn Tùy Biến (`MenuActivity`)
- **Nhãn:** `App Customer`
- **Mô tả:** Xem thực đơn theo phân loại món và tùy biến khẩu vị.
- **Checklist công việc:**
  - [ ] Header thông tin quán ăn, địa chỉ, khoảng cách km.
  - [ ] Hiển thị danh mục món ăn (Trà sữa, Cà phê, Đồ ăn vặt...).
  - [ ] Modal BottomSheet tùy biến món: Chọn Size (M/L), độ ngọt, đá, topping đi kèm.

#### Thẻ 2.3: [CUST] Giỏ Hàng Đa Quán (`CartActivity`)
- **Nhãn:** `App Customer`, `High / Urgent`
- **Mô tả:** Gom nhóm đơn hàng theo từng quán riêng biệt theo mẫu `a1.html`.
- **Checklist công việc:**
  - [ ] Tách giỏ hàng thành từng card theo quán ăn.
  - [ ] Tăng / giảm số lượng, xóa món, tự động tính tổng tiền từng quán.
  - [ ] Gợi ý các món hot bổ sung ngay trong giỏ hàng.

#### Thẻ 2.4: [CUST] Đặt Hàng & Tính Phí Ship Tự Động (`CheckoutActivity`)
- **Nhãn:** `App Customer`, `High / Urgent`
- **Mô tả:** Xác thực đơn đặt, tự động đo khoảng cách và chọn thanh toán.
- **Checklist công việc:**
  - [ ] Nhập địa chỉ giao hàng (tích hợp danh sách quận/huyện TP.HCM).
  - [ ] Tự động tính cước ship theo khoảng cách Google Maps.
  - [ ] Áp dụng mã giảm giá voucher Oishi Food.
  - [ ] Chọn hình thức: Tiền mặt (COD), Ví MoMo QR, Chuyển khoản ngân hàng.

#### Thẻ 2.5: [CUST] Màn hình Thanh Toán & Hóa Đơn Điện Tử
- **Nhãn:** `App Customer`
- **Mô tả:** Thanh toán chuyển khoản và hóa đơn thành công.
- **Checklist công việc:**
  - [ ] `PaymentActivity`: Hiển thị khung quét mã QR thanh toán ngân hàng/MoMo.
  - [ ] `OrderSuccessActivity`: Hóa đơn điện tử động, tổng tiền, mã đơn hàng.

#### Thẻ 2.6: [CUST] Theo Dõi Đơn Hàng Live & Mã QR Token (`TrackingActivity`)
- **Nhãn:** `App Customer`, `High / Urgent`
- **Mô tả:** Theo dõi tài xế di chuyển trên bản đồ theo mẫu `b9.html`.
- **Checklist công việc:**
  - [ ] Bản đồ thời gian thực hiển thị vị trí quán, nhà khách và shipper.
  - [ ] Stepper 4 bước trạng thái đơn hàng (Đã duyệt ➔ Đang làm ➔ Đang giao ➔ Xong).
  - [ ] Hiển thị **Mã QR Delivery Token** cho khách đưa shipper quét đối soát.

---

### EPICS 3: ỨNG DỤNG QUÁN ĂN / MERCHANT (`app-internal`)
**Phụ trách chính:** Thành viên C

#### Thẻ 3.1: [SHOP] Bảng Điều Khiển Đơn Hàng Kanban (`OrderListFragment`)
- **Nhãn:** `App Merchant`, `High / Urgent`
- **Mô tả:** Quản lý quy trình làm món khép kín theo mẫu `Quán/4.html`.
- **Checklist công việc:**
  - [ ] 4 cột trạng thái: Đơn Mới ➔ Đang Làm ➔ Chờ Giao ➔ Đã Xong.
  - [ ] Nút duyệt đơn, chuyển sang chế biến, báo món hoàn tất.
  - [ ] Thống kê doanh thu trong ngày và số đơn hoàn thành.

#### Thẻ 3.2: [SHOP] Điều Phối & Gán Đơn Cho Tài Xế Quán
- **Nhãn:** `App Merchant`, `High / Urgent`
- **Mô tả:** Gán tài xế nội bộ của quán nhận đơn giao.
- **Checklist công việc:**
  - [ ] Dialog danh sách tài xế đang trực (Rảnh/Bận/Offline).
  - [ ] Gọi hàm Transaction Lock gán đơn chống trùng tài xế.
  - [ ] Xử lý báo cáo sự cố (Khách không nhận / Bùng hàng).

#### Thẻ 3.3: [SHOP] Quản Lý Thực Đơn & Món Ăn (`MenuManageFragment`)
- **Nhãn:** `App Merchant`
- **Mô tả:** Thêm, sửa, xóa món ăn và combo tiết kiệm.
- **Checklist công việc:**
  - [ ] Danh sách món ăn kèm giá bán, phân loại danh mục.
  - [ ] Dialog thêm món mới, cập nhật giá tiền, trạng thái Còn/Hết hàng.
  - [ ] Quản lý các gói Combo khuyến mãi của quán.

#### Thẻ 3.4: [SHOP] Quản Lý Đội Ngũ Tài Xế Quán (`DriverListFragment`)
- **Nhãn:** `App Merchant`
- **Mô tả:** Quản lý danh sách tài xế riêng trực thuộc quán.
- **Checklist công việc:**
  - [ ] Danh sách tài xế (Mã TX, Họ tên, SĐT, Biển số xe).
  - [ ] Giám sát trạng thái ca trực: Đang trực, Tạm nghỉ, Đang đi giao.
  - [ ] Dialog thêm mới tài xế vào đội ngũ quán.

#### Thẻ 3.5: [SHOP] Hồ Sơ Quán Ăn & Cài Đặt (`StoreProfileFragment`)
- **Nhãn:** `App Merchant`
- **Mô tả:** Thông tin nhận diện và hotline cửa hàng.
- **Checklist công việc:**
  - [ ] Xem tên quán, địa chỉ đăng ký kinh doanh, giờ đóng/mở cửa.
  - [ ] Dialog chỉnh sửa thông tin quán và hotline quản lý.
  - [ ] Nút Đăng xuất an toàn.

---

### EPICS 4: ỨNG DỤNG TÀI XẾ / SHIPPER (`app-internal`)
**Phụ trách chính:** Thành viên D

#### Thẻ 4.1: [SHIP] Trang Chủ Shipper & Chạy Ghép Đơn (`ShipperFragment`)
- **Nhãn:** `App Shipper`, `High / Urgent`
- **Mô tả:** Quản lý ca trực và nhận đơn quán theo thiết kế `ship/2.html`.
- **Checklist công việc:**
  - [ ] Header Oishi Food Driver và tên Quán trực thuộc duy nhất.
  - [ ] Nút bật/tắt ca trực (🟢 ĐANG TRỰC / ⏸️ TẠM NGHỈ).
  - [ ] Thống kê ca: Thu nhập hôm nay, đơn hoàn thành, đơn đang ghép.
  - [ ] Thẻ đơn đang nhận (hỗ trợ ghép tối đa 3 đơn cùng tuyến).
  - [ ] Thẻ đơn chờ của quán (bấm nút nhận đơn ghép chạy cùng).
  - [ ] Dialog hủy đơn khi chưa lấy hàng (chọn lý do hoàn trả quán).

#### Thẻ 4.2: [SHIP] Pop-up Nhận Đơn Mới Đếm Ngược (`dialog_new_order_popup`)
- **Nhãn:** `App Shipper`
- **Mô tả:** Bật thông báo tức thời khi quán có đơn mới theo `ship/3.html`.
- **Checklist công việc:**
  - [ ] Hiển thị tiền công ship (+25.000 đ), số km, tuyến đường.
  - [ ] Đồng hồ đếm ngược 30 giây tự động đóng nếu không nhận.
  - [ ] Nút BỎ QUA và nút CHẤP NHẬN ĐƠN.

#### Thẻ 4.3: [SHIP] Quy Trình Lộ Trình Giao Hàng 4 Chặng (`DeliveryTripActivity`)
- **Nhãn:** `App Shipper`, `High / Urgent`
- **Mô tả:** Dẫn đường và đối soát lấy - giao theo `ship/4.html`, `5.html`, `6.html`, `8.html`.
- **Checklist công việc:**
  - [ ] **Chặng 1 (Đến quán):** Hộp chỉ đường Maps, gọi hotline quán, nút Đã đến quán.
  - [ ] **Chặng 2 (Lấy hàng):** Checklist tick từng món, kiểm tra niêm phong, nút Bắt đầu giao (status `DELIVERING`).
  - [ ] **Chặng 3 (Đến khách):** Dẫn đường đến nhà khách, nút Gọi điện/Nhắn tin khách, đối soát tiền COD.
  - [ ] **Chặng 4 (Quét QR):** Khung camera quét mã QR của khách, xác nhận thu tiền, hoàn tất đơn `COMPLETED`.

#### Thẻ 4.4: [SHIP] Ví Thu Nhập & Lịch Sử Chuyến Xe (`ShipperIncomeFragment`)
- **Nhãn:** `App Shipper`
- **Mô tả:** Quản lý tiền công giao hàng và lịch sử theo `ship/1.html`.
- **Checklist công việc:**
  - [ ] Thẻ ví tổng thu nhập khả dụng gradient đen sang trọng.
  - [ ] Bộ lọc thời gian: Hôm nay, Tuần này, Tháng này.
  - [ ] Danh sách các chuyến đã giao kèm tiền ship, giờ giao, đánh giá 5⭐.

#### Thẻ 4.5: [SHIP] Hồ Sơ Tài Xế Chuyên Quán (`ShipperProfileFragment`)
- **Nhãn:** `App Shipper`
- **Mô tả:** Thông tin cá nhân và định vị GPS nền theo `ship/7.html`.
- **Checklist công việc:**
  - [ ] Thẻ tài xế: Họ tên, mã TX-14, biển số xe, sao đánh giá.
  - [ ] Thẻ Quán trực thuộc duy nhất (chế độ tài xế nội bộ quán).
  - [ ] Thống kê hiệu suất: 98% Tỉ lệ nhận, 100% Hoàn thành, 0% Hủy.
  - [ ] Switch bật/tắt định vị GPS nền (`LocationForegroundService`).

---

### EPICS 5: BẢNG ĐIỀU KHIỂN WEB ADMIN (`admin.html`)
**Phụ trách chính:** Thành viên D (hoặc Thành viên A hỗ trợ)

#### Thẻ 5.1: [ADMIN] Duyệt Quán Ăn Mới & Thẩm Định KYC 4 Ảnh
- **Nhãn:** `Web Admin`, `High / Urgent`
- **Mô tả:** Phê duyệt hồ sơ cửa hàng theo quy trình định danh nghiêm ngặt.
- **Checklist công việc:**
  - [ ] Danh sách quán đăng ký mới với 3 trạng thái: Chờ duyệt, Đã duyệt, Từ chối.
  - [ ] Modal soi chi tiết 4 ảnh KYC: Mặt trước CCCD, Mặt sau, Ảnh chân dung, Ảnh mặt tiền quán.

#### Thẻ 5.2: [ADMIN] Giám Sát Đơn Hàng & Điểm Uy Tín Khách (TrustScore)
- **Nhãn:** `Web Admin`
- **Mô tả:** Cơ chế chống bùng đơn (Anti-Booming) và chế tài xử lý.
- **Checklist công việc:**
  - [ ] Tra cứu và theo dõi dữ liệu đơn hàng toàn hệ thống.
  - [ ] Quản lý điểm uy tín khách hàng TrustScore (ưu tiên COD cho khách điểm cao).
  - [ ] Xử phạt vi phạm 3 cấp độ: Cảnh báo gian hàng ➔ Ẩn quán ➔ Khóa tài khoản.

---

## 5. LỊCH TRÌNH TRIỂN KHAI THEO SPRINT (TIMELINE GỢI Ý)

```
Tuần 1: Khởi tạo Kiến trúc & Database
├── [Thành viên A] Multi-module, Firebase Database, Auth RBAC
└── [Cả nhóm] Dựng layout khung sườn các màn hình chính

Tuần 2: Xây dựng Chức năng Cốt lõi
├── [Thành viên B] App Customer: Home, Menu, Giỏ hàng đa quán
├── [Thành viên C] App Merchant: Kanban đơn hàng, CRUD Thực đơn
└── [Thành viên D] App Shipper: Trang chủ nhận đơn, Pop-up đơn mới

Tuần 3: Tích hợp Luồng Nghiệp Vụ Chéo
├── [Thành viên B] Checkout tính ship Maps, Tracking đơn hàng Live
├── [Thành viên C] Gán tài xế Transaction Lock, Quản lý đội xe
├── [Thành viên D] Lộ trình 4 chặng, Quét QR hoàn tất, Ví thu nhập
└── [Thành viên A] GPS Foreground Service, Web Admin (admin.html)

Tuần 4: Kiểm Thử, Tối Ưu & Hoàn Thiện Báo Cáo
├── [Cả nhóm] Test luồng khép kín 4 Actors: Đặt ➔ Nấu ➔ Giao ➔ Đóng đơn
└── [Cả nhóm] Chuẩn bị Slide thuyết trình, Video demo & Báo cáo chuyên đề
```
