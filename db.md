# TỪ ĐIỂN DỮ LIỆU SQL CHI TIẾT (24 BẢNG)
## HỆ THỐNG GIAO ĐỒ ĂN OISHI FOOD (4 TÁC NHÂN)

> **CÁC NGUYÊN TẮC CẢI TIẾN ĐÃ ÁP DỤNG:**
> 1. **Phân quyền Quản Trị Viên (`ADMIN`) trực tiếp qua `users`:** Không cần bảng phụ `admins`, quản trị viên được định danh qua `users.role_id` liên kết bảng `roles`.
> 2. **Bảng Tài Xế (`drivers`) riêng biệt:** Chuẩn hóa hồ sơ riêng cho Actor DRIVER (mã tài xế `driver_code`, CCCD `cccd_number`, GPLX `license_number`, loại xe `vehicle_type`, biển số `vehicle_plate`, tọa độ GPS và ca trực `duty_status`).
> 3. **Tách riêng Chủ Quán (`merchants`) & Cửa Hàng (`stores`):** Chuẩn hóa quan hệ 1 - N (`merchants` → `stores`). Hồ sơ pháp lý CCCD 4 ảnh & giấy phép thuộc `merchants`, thông tin thương hiệu & hoạt động kinh doanh thuộc `stores`.
> 4. **Mô hình 3 nhóm Actor chuyên biệt kế thừa từ `users`:** Cả 3 nhóm hồ sơ (`customers`, `merchants`, `drivers`) đều có bảng hồ sơ thực thể chuyên biệt liên kết khóa ngoại 1-1 với bảng trung tâm `users`.
> 5. **Bảng Vai Trò (`roles`) & Trạng Thái (`user_statuses`) riêng biệt:** Chuẩn hóa quan hệ 1 - N với `users`.
> 6. **Ảnh đại diện (`avatar_url`) dùng chung:** Đặt tại bảng trung tâm `users` (dùng chung cho toàn bộ các Actors).
> 7. **Hợp nhất địa chỉ:** Sử dụng chuỗi địa chỉ thống nhất (`street_address` / `address`).
> 8. **Bổ sung Đánh giá món ăn (`dish_reviews`):** Đánh giá món ăn riêng biệt (số sao 1-5, nhận xét, ảnh chụp).
> 9. **Bổ sung Lịch sử biến động Điểm uy tín (`trust_score_logs`):** Điểm khởi tạo mặc định 0, hoàn tất đơn cộng điểm, bom hàng reset về 0 và lưu nhật ký biến động.
> 10. **Tách riêng Bảng Tài Khoản Ngân Hàng (`bank_accounts`):** Lưu trữ thông tin ngân hàng thanh toán doanh thu/thù lao độc lập, liên kết `user_id`.
> 11. **Tách riêng Bảng Trạng Thái Xét Duyệt (`approval_statuses`):** Chuẩn hóa danh mục trạng thái xét duyệt hồ sơ đối tác (`PENDING`, `APPROVED`, `REJECTED`), phân tách rõ ràng và liên kết FK chuẩn CSDL quan hệ.
> 12. **Tách riêng Bảng Trạng Thái Cửa Hàng (`store_statuses`):** Chuẩn hóa danh mục trạng thái kinh doanh của gian hàng (`PENDING`, `OPEN`, `CLOSED`, `LOCKED`).
> 13. **Tách riêng Bảng Chế Tài Xử Phạt (`penalty_tiers`):** Chuẩn hóa các cấp độ chế tài vi phạm của Admin đối với quán ăn (`NONE`, `WARN`, `HIDE`, `LOCK`).
> 14. **Tách riêng Bảng Khung Thời Gian Giao Hàng (`delivery_time_frames`):** Chuẩn hóa danh mục khoảng thời gian giao hàng dự kiến (`15-25 phút`, `20-30 phút`, `25-35 phút`, `30-45 phút`).

---

### 3.1. Bảng 1: `roles` (Vai trò người dùng hệ thống - RBAC)
*Quản lý danh mục các vai trò và quyền hạn trong hệ thống.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `role_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Mã định danh vai trò (1: ADMIN, 2: CUSTOMER, 3: MERCHANT, 4: DRIVER) |
| `role_code` | VARCHAR(20) | NOT NULL, UNIQUE | Mã code nhận diện (`ADMIN`, `CUSTOMER`, `MERCHANT`, `DRIVER`) |
| `role_name` | VARCHAR(50) | NOT NULL | Tên vai trò hiển thị (Quản trị viên, Khách hàng, Chủ quán, Tài xế) |
| `description` | VARCHAR(255) | NULL | Mô tả quyền hạn của vai trò |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm tạo vai trò |

---

### 3.2. Bảng 2: `user_statuses` (Trạng thái hoạt động tài khoản)
*Quản lý trạng thái hoạt động, đăng nhập/đăng xuất của người dùng.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `status_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Mã trạng thái (1: ACTIVE, 2: INACTIVE, 3: OFFLINE) |
| `status_code` | VARCHAR(20) | NOT NULL, UNIQUE | Mã định danh trạng thái (`ACTIVE`, `INACTIVE`, `OFFLINE`) |
| `status_name` | VARCHAR(50) | NOT NULL | Tên trạng thái (Đang hoạt động, Ngưng hoạt động, Ngoại tuyến) |
| `description` | VARCHAR(255) | NULL | Ý nghĩa trạng thái |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm khởi tạo |

---

### 3.3. Bảng 3: `users` (Tài khoản người dùng hệ thống)
*Lưu trữ định danh và xác thực tập trung cho toàn bộ 4 tác nhân.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `user_id` | VARCHAR(64) | PRIMARY KEY | Khóa chính duy nhất (Khớp Firebase Auth UID) |
| `role_id` | INT | NOT NULL, FK(`roles.role_id`)| **Liên kết vai trò tài khoản** |
| `status_id` | INT | NOT NULL, DEFAULT 1, FK(`user_statuses.status_id`)| **Liên kết trạng thái tài khoản** |
| `phone_number` | VARCHAR(15) | NOT NULL | Số điện thoại liên hệ đăng nhập |
| `password_hash`| VARCHAR(255) | NOT NULL | Chuỗi băm mật khẩu bảo mật |
| `full_name` | VARCHAR(100) | NOT NULL | Họ và tên hiển thị của người dùng |
| `email` | VARCHAR(100) | UNIQUE, NULL | Địa chỉ email liên lạc |
| `avatar_url` | VARCHAR(255) | NULL | **Ảnh đại diện dùng chung (Admin, Customer, Quán, Shipper)** |
| `fcm_token` | TEXT | NULL | Token Firebase Cloud Messaging nhận thông báo |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm đăng ký tài khoản |

---

### 3.4. Bảng 4: `customers` (Hồ sơ khách hàng & Điểm Uy Tín)
*Lưu trữ hồ sơ tín nhiệm khách hàng, phục vụ chống boom hàng.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `user_id` | VARCHAR(64) | PRIMARY KEY, FK(`users.user_id`) | Khóa chính liên kết bảng `users` |
| `trust_score` | INT | DEFAULT 0, CHECK(>= 0) | **Điểm uy tín** (Khởi tạo 0 điểm, mỗi đơn hàng hoàn thành được cộng điểm, bom hàng reset về 0) |
| `total_orders` | INT | DEFAULT 0 | Tổng số đơn hàng đã đặt trên hệ thống |
| `completed_orders`| INT | DEFAULT 0 | Số đơn hàng đã nhận thành công |
| `boom_orders` | INT | DEFAULT 0 | Số lần từ chối nhận hàng không lý do |

---

### 3.5. Bảng 5: `customer_addresses` (Sổ địa chỉ nhận hàng)
*Danh bạ địa chỉ khách hàng tại khu vực TP. Hồ Chí Minh.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `address_id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Mã địa chỉ |
| `user_id` | VARCHAR(64) | FK(`customers.user_id`) | Khách hàng sở hữu (Khóa ngoại liên kết customers) |
| `receiver_name`| VARCHAR(100) | NOT NULL | Tên người nhận tại điểm giao |
| `receiver_phone`| VARCHAR(15)| NOT NULL | Số điện thoại người nhận |
| `street_address`| VARCHAR(255)| NOT NULL | **Địa chỉ nhận hàng chi tiết (Số nhà, tên đường, khu vực)** |
| `latitude` | DECIMAL(10,8)| NULL | Tọa độ GPS vĩ độ |
| `longitude` | DECIMAL(11,8)| NULL | Tọa độ GPS kinh độ |
| `is_default` | BOOLEAN | DEFAULT FALSE | Đánh dấu địa chỉ nhận mặc định |

---

### 3.6. Bảng 6: `merchants` (Hồ sơ chủ cửa hàng / Đối tác - Actor MERCHANT)
*Thông tin định danh và hồ sơ pháp lý CCCD của Chủ quán để Admin xét duyệt.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `user_id` | VARCHAR(64) | PRIMARY KEY, FK(`users.user_id`) | Khóa chính liên kết `users` (Actor MERCHANT) |
| `cccd_number` | VARCHAR(20) | NOT NULL | Số Căn cước công dân của chủ quán |
| `cccd_front_url`| VARCHAR(255)| NOT NULL | Ảnh mặt trước CCCD |
| `cccd_back_url` | VARCHAR(255)| NOT NULL | Ảnh mặt sau CCCD |
| `cccd_hold_url` | VARCHAR(255)| NOT NULL | Ảnh chân dung cầm CCCD trên tay |
| `cccd_portrait_url`| VARCHAR(255)| NULL | Ảnh chân dung đối chiếu |
| `business_license`| VARCHAR(255)| NULL | Ảnh Giấy phép ĐKKD / Vệ sinh ATTP |
| `tax_code` | VARCHAR(20) | NULL | Mã số thuế hộ kinh doanh / doanh nghiệp |
| `approval_status_id`| INT | NOT NULL, DEFAULT 1, FK(`approval_statuses.status_id`) | **Khóa ngoại liên kết bảng trạng thái xét duyệt hồ sơ** |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm nộp hồ sơ |

---

### 3.7. Bảng 7: `approval_statuses` (Trạng thái xét duyệt hồ sơ đối tác)
*Danh mục chuẩn hóa trạng thái kiểm duyệt hồ sơ đối tác (Merchant/Driver) của Quản trị viên.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `status_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Mã định danh trạng thái xét duyệt (1: PENDING, 2: APPROVED, 3: REJECTED) |
| `status_code` | VARCHAR(20) | NOT NULL, UNIQUE | Mã định danh (`PENDING`, `APPROVED`, `REJECTED`) |
| `status_name` | VARCHAR(50) | NOT NULL | Tên trạng thái hiển thị (Chờ duyệt, Đã duyệt, Từ chối) |
| `description` | VARCHAR(255) | NULL | Ý nghĩa trạng thái xét duyệt |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm khởi tạo |

---

### 3.8. Bảng 8: `bank_accounts` (Tài khoản ngân hàng thụ hưởng)
*Lưu trữ thông tin tài khoản ngân hàng nhận thanh toán doanh thu của chủ quán, thù lao tài xế.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `bank_account_id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Mã tài khoản ngân hàng |
| `user_id` | VARCHAR(64) | NOT NULL, FK(`users.user_id`) | Khóa ngoại liên kết tài khoản người dùng (`merchants`, `drivers`) |
| `bank_name` | VARCHAR(100) | NOT NULL | Tên ngân hàng nhận thanh toán doanh thu |
| `account_number` | VARCHAR(30) | NOT NULL | **Số tài khoản ngân hàng thụ hưởng** |
| `account_holder_name`| VARCHAR(100) | NOT NULL | Họ và tên chủ tài khoản thụ hưởng |
| `branch_name` | VARCHAR(100) | NULL | Chi nhánh mở tài khoản ngân hàng |
| `is_default` | BOOLEAN | DEFAULT TRUE | Đặt làm tài khoản nhận thanh toán mặc định |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm thêm tài khoản |

---

### 3.9. Bảng 9: `store_statuses` (Trạng thái hoạt động gian hàng)
*Danh mục chuẩn hóa trạng thái kinh doanh của gian hàng trên ứng dụng.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `status_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Mã định danh trạng thái gian hàng (1: PENDING, 2: OPEN, 3: CLOSED, 4: LOCKED) |
| `status_code` | VARCHAR(20) | NOT NULL, UNIQUE | Mã định danh (`PENDING`, `OPEN`, `CLOSED`, `LOCKED`) |
| `status_name` | VARCHAR(50) | NOT NULL | Tên trạng thái hiển thị (Chờ duyệt, Đang mở cửa, Đang đóng cửa, Bị khóa) |
| `description` | VARCHAR(255) | NULL | Diễn giải ý nghĩa trạng thái |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm khởi tạo |

---

### 3.10. Bảng 10: `penalty_tiers` (Cấp độ chế tài xử phạt quán ăn)
*Danh mục chế tài xử phạt do Quản trị viên áp dụng khi giải quyết khiếu nại.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `tier_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Mã định danh cấp độ phạt (1: NONE, 2: WARN, 3: HIDE, 4: LOCK) |
| `tier_code` | VARCHAR(20) | NOT NULL, UNIQUE | Mã cấp độ (`NONE`, `WARN`, `HIDE`, `LOCK`) |
| `tier_name` | VARCHAR(50) | NOT NULL | Tên mức phạt hiển thị (Không phạt, Cảnh cáo, Ẩn món/quán, Khóa quán) |
| `description` | VARCHAR(255) | NULL | Chi tiết quy định áp dụng chế tài |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm khởi tạo |

---

### 3.11. Bảng 11: `delivery_time_frames` (Khung thời gian giao hàng dự kiến)
*Danh mục chuẩn hóa các mốc thời gian giao hàng ước tính của quán ăn.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `frame_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Mã định danh khung thời gian (1: 15-25 phút, 2: 20-30 phút, 3: 25-35 phút, 4: 30-45 phút) |
| `frame_code` | VARCHAR(20) | NOT NULL, UNIQUE | Mã định danh (`15-25MIN`, `20-30MIN`, `25-35MIN`, `30-45MIN`) |
| `display_text`| VARCHAR(50) | NOT NULL | Chuỗi hiển thị (`15-25 phút`, `20-30 phút`, `25-35 phút`, `30-45 phút`) |
| `min_minutes` | INT | NOT NULL | Thời gian tối thiểu (phút) |
| `max_minutes` | INT | NOT NULL | Thời gian tối đa (phút) |
| `is_active` | BOOLEAN | DEFAULT TRUE | Trạng thái áp dụng |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm khởi tạo |

---

### 3.12. Bảng 12: `stores` (Gian hàng cửa hàng / Quán ăn)
*Thông tin gian hàng kinh doanh trên ứng dụng (thuộc sở hữu của Chủ quán `merchants`).*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `store_id` | VARCHAR(64) | PRIMARY KEY | Mã gian hàng (Khớp Firebase key) |
| `merchant_id` | VARCHAR(64) | FK(`merchants.user_id`)| **Chủ quán sở hữu gian hàng** |
| `name` | VARCHAR(150) | NOT NULL | Tên thương hiệu quán ăn |
| `phone` | VARCHAR(15) | NOT NULL | Hotline liên hệ quán |
| `address` | VARCHAR(255) | NOT NULL | **Địa chỉ quán ăn thống nhất (TP.HCM)** |
| `latitude` | DECIMAL(10,8)| NOT NULL | Tọa độ GPS vĩ độ quán |
| `longitude` | DECIMAL(11,8)| NOT NULL | Tọa độ GPS kinh độ quán |
| `image_url` | TEXT | NULL | Ảnh biển hiệu / ảnh bìa gian hàng |
| `open_time` | VARCHAR(10) | NOT NULL | Giờ mở cửa (VD: `07:00`) |
| `close_time` | VARCHAR(10) | NOT NULL | Giờ đóng cửa (VD: `22:00`) |
| `status_id` | INT | NOT NULL, DEFAULT 1, FK(`store_statuses.status_id`) | **Khóa ngoại trạng thái quán** (1: PENDING, 2: OPEN, 3: CLOSED, 4: LOCKED) |
| `penalty_tier_id`| INT | NOT NULL, DEFAULT 1, FK(`penalty_tiers.tier_id`) | **Khóa ngoại chế tài xử phạt** (1: NONE, 2: WARN, 3: HIDE, 4: LOCK) |
| `penalty_reason`| TEXT | NULL | Lý do xử phạt quán nếu vi phạm |
| `rating_avg` | DECIMAL(2,1) | DEFAULT 5.0 | Điểm đánh giá trung bình của quán |
| `delivery_time_frame_id`| INT | NOT NULL, DEFAULT 2, FK(`delivery_time_frames.frame_id`) | **Khóa ngoại khung thời gian giao hàng dự kiến** |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm tạo gian hàng |

---

### 3.13. Bảng 13: `drivers` (Hồ sơ tài xế giao hàng - Actor DRIVER)
*Hồ sơ tài xế chuyên trách nhận đơn, giao hàng, cập nhật tọa độ GPS và xác thực giao đơn.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `user_id` | VARCHAR(64) | PRIMARY KEY, FK(`users.user_id`) | Khóa chính liên kết `users` (Actor DRIVER) |
| `store_id` | VARCHAR(64) | NULL, FK(`stores.store_id`) | Quán trực thuộc (hoặc NULL nếu là tài xế nền tảng) |
| `driver_code` | VARCHAR(20) | NOT NULL, UNIQUE | Mã tài xế (Ví dụ: `TX-19`, `TX-14`, `TX-08`) |
| `cccd_number` | VARCHAR(20) | NOT NULL | Số Căn cước công dân (12 số) |
| `cccd_front_url`| VARCHAR(255)| NULL | Ảnh mặt trước CCCD |
| `cccd_back_url` | VARCHAR(255)| NULL | Ảnh mặt sau CCCD |
| `license_number`| VARCHAR(30) | NULL | Số Giấy phép lái xe / GPLX A1, A2 |
| `vehicle_type`| VARCHAR(50) | DEFAULT 'MOTORBIKE' | Loại phương tiện (Xe máy số, tay ga, xe điện) |
| `vehicle_plate`| VARCHAR(20) | NOT NULL | Biển kiểm soát xe gắn máy |
| `rating_avg` | DECIMAL(2,1) | DEFAULT 5.0 | Điểm sao phục vụ từ khách hàng |
| `duty_status` | ENUM | DEFAULT 'OFF_DUTY' | `AVAILABLE` (sẵn sàng), `DELIVERING` (đang giao), `OFF_DUTY` (nghỉ ca) |
| `current_lat` | DECIMAL(10,8)| NULL | Tọa độ GPS vĩ độ cập nhật thời gian thực |
| `current_lng` | DECIMAL(11,8)| NULL | Tọa độ GPS kinh độ cập nhật thời gian thực |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm kích hoạt hồ sơ tài xế |

---

### 3.14. Bảng 14: `categories` (Danh mục ẩm thực)
*Phân loại món ăn toàn hệ thống (Cơm, Bún/Phở, Trà sữa, Gà rán...).*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `category_id` | VARCHAR(32) | PRIMARY KEY | Mã danh mục |
| `name` | VARCHAR(100) | NOT NULL, UNIQUE | Tên danh mục hiển thị |
| `icon_url` | VARCHAR(255) | NULL | Biểu tượng icon/emoji minh họa |
| `display_order`| INT | DEFAULT 0 | Thứ tự hiển thị ưu tiên |
| `is_active` | BOOLEAN | DEFAULT TRUE | Trạng thái kích hoạt |

---

### 3.15. Bảng 15: `dishes` (Thực đơn món ăn)
*Các món ăn do quán đăng bán trên hệ thống.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `dish_id` | VARCHAR(64) | PRIMARY KEY | Mã món ăn |
| `store_id` | VARCHAR(64) | FK(`stores.store_id`) | Thuộc gian hàng nào |
| `category_id`| VARCHAR(32) | FK(`categories.category_id`)| Danh mục món ăn |
| `name` | VARCHAR(150) | NOT NULL | Tên món ăn |
| `description`| TEXT | NULL | Mô tả nguyên liệu / hương vị |
| `image_url` | VARCHAR(255) | NULL | Hình ảnh món ăn chụp thực tế |
| `base_price` | DECIMAL(12,0)| NOT NULL, CHECK(>= 0) | Đơn giá niêm yết (VNĐ) |
| `is_available`| BOOLEAN | DEFAULT TRUE | Tình trạng còn hàng |
| `is_hidden` | BOOLEAN | DEFAULT FALSE | Bị ẩn bởi quán hoặc do chế tài `HIDE` |
| `sold_count` | INT | DEFAULT 0 | Lũy kế số lượng đã bán |
| `rating_avg` | DECIMAL(2,1) | DEFAULT 5.0 | Điểm sao đánh giá trung bình của món |
| `review_count`| INT | DEFAULT 0 | Tổng số lượt khách đánh giá món này |

---

### 3.16. Bảng 16: `combos` & Bảng 17: `combo_items` (Gói Combo Khuyến Mãi)
*Tập hợp nhiều món ăn bán chung với mức giá ưu đãi kèm danh sách ID món ăn.*

#### Bảng 16: `combos`
| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `combo_id` | VARCHAR(64) | PRIMARY KEY | Mã gói combo |
| `store_id` | VARCHAR(64) | FK(`stores.store_id`) | Quán sở hữu combo |
| `combo_name` | VARCHAR(150) | NOT NULL | Tên combo (VD: `Combo Bữa Trưa Tiết Kiệm`) |
| `description`| TEXT | NULL | Chi tiết gói |
| `image_url` | VARCHAR(255) | NULL | Hình ảnh combo |
| `combo_price`| DECIMAL(12,0)| NOT NULL | Giá trọn gói ưu đãi (VNĐ) |
| `dish_ids` | TEXT | NOT NULL | **Mảng JSON danh sách ID món ăn trong combo** |
| `is_active` | BOOLEAN | DEFAULT TRUE | Trạng thái mở bán |

#### Bảng 17: `combo_items`
| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `combo_item_id`| BIGINT | PRIMARY KEY, AUTO_INCREMENT | Mã chi tiết món trong combo |
| `combo_id` | VARCHAR(64) | FK(`combos.combo_id`) | Thuộc gói combo nào |
| `dish_id` | VARCHAR(64) | FK(`dishes.dish_id`) | Mã món ăn cấu thành |
| `quantity` | INT | NOT NULL, DEFAULT 1 | Số lượng món trong combo |

---

### 3.17. Bảng 18: `shipping_rules` (Cấu hình biểu phí vận chuyển TP.HCM)
*Biểu phí vận chuyển động theo cự ly khoảng cách kilomet.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `rule_id` | INT | PRIMARY KEY, AUTO_INCREMENT | Mã quy tắc |
| `from_km` | DECIMAL(4,1)| NOT NULL | Từ khoảng cách (km) |
| `to_km` | DECIMAL(4,1)| NOT NULL | Đến khoảng cách (km) |
| `base_fee` | DECIMAL(12,0)| NOT NULL | Mức phí giao áp dụng (VNĐ) |
| `is_active` | BOOLEAN | DEFAULT TRUE | Hiệu lực áp dụng |

---

### 3.18. Bảng 19: `orders` (Đơn hàng)
*Quản lý vòng đời đơn từ khi tạo đến khi giao hoàn tất.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `order_id` | VARCHAR(64) | PRIMARY KEY | Mã đơn hàng (Khớp Firebase key) |
| `customer_id` | VARCHAR(64) | FK(`customers.user_id`) | Khách hàng đặt đơn |
| `store_id` | VARCHAR(64) | FK(`stores.store_id`) | Quán tiếp nhận chế biến |
| `driver_id` | VARCHAR(64) | NULL, FK(`drivers.user_id`)| Tài xế được quán chỉ định giao |
| `status` | ENUM | NOT NULL | `PENDING` → `CONFIRMED` → `PREPARING` → `DELIVERING` → `COMPLETED` / `CANCELLED` |
| `subtotal_amount`| DECIMAL(12,0)| NOT NULL | Tiền món ăn |
| `shipping_fee`| DECIMAL(12,0)| NOT NULL | Phí vận chuyển |
| `total_amount`| DECIMAL(12,0)| NOT NULL | Tổng thanh toán = Món + Ship |
| `payment_method`| ENUM | NOT NULL | `COD` (Tiền mặt), `MOMO` (Ví điện tử), `BANKING` (Chuyển khoản ngân hàng) |
| `payment_status`| ENUM | DEFAULT 'PENDING' | `PENDING`, `PAID`, `REFUNDED` |
| `delivery_address`| VARCHAR(255)| NOT NULL | **Địa chỉ nhận hàng chi tiết** |
| `delivery_token`| VARCHAR(6) | NOT NULL | **Mã OTP/QR xác thực giao hàng 6 số** |
| `boom_reported`| BOOLEAN | DEFAULT FALSE | Đánh dấu sự cố khách boom hàng |
| `created_at` | BIGINT | NOT NULL | Thời điểm đặt (Epoch milliseconds) |

---

### 3.19. Bảng 20: `order_items` (Chi tiết món trong đơn)
*Lưu giữ giá bán tại thời điểm đặt (tránh ảnh hưởng khi quán đổi giá menu sau này).*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `item_id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Mã chi tiết món |
| `order_id` | VARCHAR(64) | FK(`orders.order_id`) | Thuộc đơn hàng nào |
| `dish_id` | VARCHAR(64) | FK(`dishes.dish_id`) | Mã món ăn gốc |
| `dish_name` | VARCHAR(150) | NOT NULL | Tên món tại thời điểm đặt |
| `unit_price` | DECIMAL(12,0)| NOT NULL | Đơn giá lúc mua |
| `quantity` | INT | NOT NULL, CHECK(> 0) | Số lượng mua |
| `subtotal` | DECIMAL(12,0)| NOT NULL | Thành tiền = Đơn giá × Số lượng |

---

### 3.20. Bảng 21: `complaints` (Khiếu nại & Tranh chấp)
*Khách hàng gửi phản hồi khi đồ ăn hỏng, giao thiếu món hoặc thái độ phục vụ.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `complaint_id`| VARCHAR(64) | PRIMARY KEY | Mã khiếu nại |
| `order_id` | VARCHAR(64) | FK(`orders.order_id`) | Đơn hàng phát sinh khiếu nại |
| `customer_id` | VARCHAR(64) | FK(`customers.user_id`) | Người khiếu nại |
| `store_id` | VARCHAR(64) | FK(`stores.store_id`) | Quán bị khiếu nại |
| `content` | TEXT | NOT NULL | Nội dung chi tiết phản ánh |
| `status` | ENUM | DEFAULT 'PENDING' | `PENDING`, `RESOLVED`, `REJECTED` |
| `admin_resolution`| TEXT | NULL | Kết luận & hình thức chế tài của Admin (`WARN`, `HIDE`, `LOCK`) |
| `created_at` | BIGINT | NOT NULL | Thời điểm gửi |

---

### 3.21. Bảng 22: `tracking_locations` (Theo dõi lộ trình tài xế)
*Lưu tọa độ GPS định vị di chuyển thời gian thực của Shipper khi đang giao hàng.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `tracking_id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Mã ghi nhận |
| `order_id` | VARCHAR(64) | FK(`orders.order_id`) | Đơn hàng đang theo dõi |
| `driver_id` | VARCHAR(64) | FK(`drivers.user_id`) | Tài xế thực hiện đơn |
| `latitude` | DECIMAL(10,8)| NOT NULL | Vĩ độ hiện thời |
| `longitude` | DECIMAL(11,8)| NOT NULL | Kinh độ hiện thời |
| `updated_at` | BIGINT | NOT NULL | Thời điểm ghi nhận (Epoch ms) |

---

### 3.22. Bảng 23: `dish_reviews` (Đánh giá món ăn từ khách hàng)
*Khách hàng đánh giá số sao và để lại nhận xét, hình ảnh sau khi hoàn tất đơn hàng.*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `review_id` | VARCHAR(64) | PRIMARY KEY | Mã đánh giá (UUID/Firebase key) |
| `dish_id` | VARCHAR(64) | FK(`dishes.dish_id`) | Món ăn được đánh giá |
| `order_id` | VARCHAR(64) | FK(`orders.order_id`) | Đơn hàng đã đặt món này |
| `customer_id` | VARCHAR(64) | FK(`customers.user_id`) | Khách hàng gửi đánh giá |
| `customer_name`| VARCHAR(100) | NOT NULL | Tên hiển thị người đánh giá |
| `customer_avatar`| VARCHAR(255)| NULL | Kế thừa từ `users.avatar_url` |
| `rating` | INT | NOT NULL, CHECK(1..5) | Số sao đánh giá (1 đến 5 sao) |
| `comment` | TEXT | NULL | Lời nhận xét về món ăn |
| `image_url` | VARCHAR(255) | NULL | Ảnh chụp món ăn thực tế do khách chụp |
| `created_at` | BIGINT | NOT NULL | Thời điểm đánh giá (Epoch ms) |

---

### 3.23. Bảng 24: `trust_score_logs` (Lịch sử biến động điểm uy tín khách hàng)
*Lưu vết nhật ký lịch sử cộng/trừ điểm uy tín khách hàng (hoàn tất đơn cộng điểm, bom hàng reset về 0).*

| Tên Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa / Mô Tả |
| :--- | :--- | :--- | :--- |
| `log_id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Mã ghi nhận biến động điểm uy tín |
| `user_id` | VARCHAR(64) | FK(`customers.user_id`) | Khách hàng nhận biến động điểm |
| `order_id` | VARCHAR(64) | NULL, FK(`orders.order_id`) | Đơn hàng phát sinh biến động (nếu có) |
| `change_amount` | INT | NOT NULL | Số điểm thay đổi (VD: `+1` khi giao thành công, reset về `0` khi bom hàng) |
| `old_score` | INT | NOT NULL | Điểm uy tín trước khi biến động |
| `new_score` | INT | NOT NULL | Điểm uy tín sau khi biến động |
| `reason` | VARCHAR(255) | NOT NULL | Lý do (`Hoàn thành đơn hàng`, `Từ chối nhận hàng / Bom hàng`) |
| `created_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | Thời điểm ghi nhận biến động |