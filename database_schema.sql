-- ============================================================
-- HỆ THỐNG ĐẶT ĐỒ ĂN TRỰC TUYẾN OISHI FOOD (CHUYÊN ĐỀ 2)
-- SCRIPT TẠO CSDL — KIẾN TRÚC 4 ACTORS (24 BẢNG)
-- Dựa theo giao diện repo: tranvu2006-ui/chuyen-de-2-android
-- ============================================================

CREATE DATABASE IF NOT EXISTS oishi_food_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE oishi_food_db;

-- Xóa bảng theo thứ tự phụ thuộc Foreign Key (con → cha)
DROP TABLE IF EXISTS trust_score_logs;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS dish_reviews;
DROP TABLE IF EXISTS complaints;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS combo_items;
DROP TABLE IF EXISTS combos;
DROP TABLE IF EXISTS dishes;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS drivers;
DROP TABLE IF EXISTS customer_addresses;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS stores;
DROP TABLE IF EXISTS delivery_time_frames;
DROP TABLE IF EXISTS penalty_tiers;
DROP TABLE IF EXISTS store_statuses;
DROP TABLE IF EXISTS bank_accounts;
DROP TABLE IF EXISTS merchants;
DROP TABLE IF EXISTS approval_statuses;
DROP TABLE IF EXISTS shipping_rules;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS carts;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS user_statuses;
DROP TABLE IF EXISTS roles;

-- ============================================================
-- 1. BẢNG VAI TRÒ NGƯỜI DÙNG (ROLES)
-- Định nghĩa 4 nhóm tác nhân trong hệ thống Oishi Food
-- ============================================================
CREATE TABLE roles (
    role_id     INT AUTO_INCREMENT PRIMARY KEY,
    role_code   VARCHAR(20)  NOT NULL UNIQUE, -- 'ADMIN', 'CUSTOMER', 'MERCHANT', 'DRIVER'
    role_name   VARCHAR(50)  NOT NULL,        -- 'Quản trị viên', 'Khách hàng', 'Chủ cửa hàng', 'Tài xế giao hàng'
    description VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL
) ENGINE=InnoDB;

-- ============================================================
-- 2. BẢNG TRẠNG THÁI TÀI KHOẢN (USER_STATUSES)
-- Định nghĩa trạng thái hoạt động của tài khoản
-- ============================================================
CREATE TABLE user_statuses (
    status_id   INT AUTO_INCREMENT PRIMARY KEY,
    status_code VARCHAR(20)  NOT NULL UNIQUE, -- 'ACTIVE', 'INACTIVE', 'OFFLINE'
    status_name VARCHAR(50)  NOT NULL,        -- 'Đang hoạt động', 'Ngưng hoạt động', 'Ngoại tuyến'
    description VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL
) ENGINE=InnoDB;

-- ============================================================
-- 3. BẢNG TÀI KHOẢN NGƯỜI DÙNG (USERS)
-- Liên kết khóa ngoại FK: ROLES (role_id) & USER_STATUSES (status_id)
-- Thuộc tính dùng chung: avatar_url áp dụng cho cả Khách, Quán, Shipper
-- ============================================================
CREATE TABLE users (
    user_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id       INT          NOT NULL,
    status_id     INT          DEFAULT 1 NOT NULL,
    phone_number  VARCHAR(15)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) UNIQUE,
    avatar_url    VARCHAR(255) DEFAULT NULL,
    is_active     BOOLEAN      DEFAULT TRUE NOT NULL,
    fcm_token     TEXT,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (role_id) REFERENCES roles(role_id) ON UPDATE CASCADE,
    FOREIGN KEY (status_id) REFERENCES user_statuses(status_id) ON UPDATE CASCADE,
    INDEX idx_users_role (role_id),
    INDEX idx_users_status (status_id)
) ENGINE=InnoDB;

-- ============================================================
-- 4. BẢNG HỒ SƠ KHÁCH HÀNG (CUSTOMERS)
-- Giao diện: khách/b11.html — Trang cá nhân + Điểm Uy Tín
-- (Ảnh đại diện avatar kế thừa từ bảng users)
-- ============================================================
CREATE TABLE customers (
    user_id          BIGINT PRIMARY KEY,
    trust_score      INT          DEFAULT 0 NOT NULL CHECK (trust_score >= 0),
    total_orders     INT          DEFAULT 0 NOT NULL,
    completed_orders INT          DEFAULT 0 NOT NULL,
    boom_orders      INT          DEFAULT 0 NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 3. BẢNG SỔ ĐỊA CHỈ GIAO HÀNG (CUSTOMER_ADDRESSES)
-- Giao diện: khách/b8.html — Checkout chọn địa chỉ nhận hàng
-- ============================================================
CREATE TABLE customer_addresses (
    address_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    receiver_name  VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(15)  NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    latitude       DECIMAL(10,8),
    longitude      DECIMAL(11,8),
    is_default     BOOLEAN      DEFAULT FALSE NOT NULL,
    FOREIGN KEY (user_id) REFERENCES customers(user_id) ON DELETE CASCADE,
    INDEX idx_addr_customer (user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 4. BẢNG DANH MỤC NGÀNH HÀNG (CATEGORIES)
-- Giao diện: khách/b1.html — Grid danh mục (Cơm, Trà sữa, Phở, Gà)
-- ============================================================
CREATE TABLE categories (
    category_id   INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    icon_url      VARCHAR(255),
    display_order INT          DEFAULT 0 NOT NULL,
    is_active     BOOLEAN      DEFAULT TRUE NOT NULL
) ENGINE=InnoDB;

-- ============================================================
-- 5. BẢNG TRẠNG THÁI XÉT DUYỆT HỒ SƠ (APPROVAL_STATUSES)
-- Định nghĩa danh mục trạng thái xét duyệt của Admin
-- ============================================================
CREATE TABLE approval_statuses (
    status_id   INT AUTO_INCREMENT PRIMARY KEY,
    status_code VARCHAR(20)  NOT NULL UNIQUE, -- 'PENDING', 'APPROVED', 'REJECTED'
    status_name VARCHAR(50)  NOT NULL,        -- 'Chờ duyệt', 'Đã duyệt', 'Từ chối'
    description VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL
) ENGINE=InnoDB;

-- ============================================================
-- 6. BẢNG CHỦ CỬA HÀNG / ĐỐI TÁC (MERCHANTS)
-- Giao diện: Quán/8.html — Hồ sơ pháp lý CCCD 4 ảnh & giấy phép
-- ============================================================
CREATE TABLE merchants (
    user_id            BIGINT PRIMARY KEY,
    cccd_number        VARCHAR(20)  NOT NULL,
    cccd_front_url     VARCHAR(255),
    cccd_back_url      VARCHAR(255),
    cccd_hold_url      VARCHAR(255),
    cccd_portrait_url  VARCHAR(255),
    business_license   VARCHAR(255),
    tax_code           VARCHAR(20),
    approval_status_id INT          DEFAULT 1 NOT NULL,
    created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (approval_status_id) REFERENCES approval_statuses(status_id) ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 6. BẢNG TÀI KHOẢN NGÂN HÀNG THỤ HƯỞNG (BANK_ACCOUNTS)
-- Quản lý tài khoản ngân hàng nhận thanh toán doanh thu/thù lao
-- ============================================================
CREATE TABLE bank_accounts (
    bank_account_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    bank_name           VARCHAR(100) NOT NULL,
    account_number      VARCHAR(30)  NOT NULL,
    account_holder_name VARCHAR(100) NOT NULL,
    branch_name         VARCHAR(100) DEFAULT NULL,
    is_default          BOOLEAN      DEFAULT TRUE NOT NULL,
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_bank_user (user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 8. BẢNG TRẠNG THÁI GIAN HÀNG (STORE_STATUSES)
-- Danh mục trạng thái hoạt động kinh doanh của quán
-- ============================================================
CREATE TABLE store_statuses (
    status_id   INT AUTO_INCREMENT PRIMARY KEY,
    status_code VARCHAR(20)  NOT NULL UNIQUE, -- 'PENDING', 'OPEN', 'CLOSED', 'LOCKED'
    status_name VARCHAR(50)  NOT NULL,        -- 'Chờ duyệt', 'Đang mở cửa', 'Đang đóng cửa', 'Bị khóa'
    description VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL
) ENGINE=InnoDB;

-- ============================================================
-- 9. BẢNG CHẾ TÀI XỬ PHẠT QUÁN ĂN (PENALTY_TIERS)
-- ============================================================
-- 10. BẢNG CẤP ĐỘ CHẾ TÀI QUÁN (PENALTY_TIERS)
-- Danh mục cấp độ chế tài áp dụng cho quán vi phạm
-- ============================================================
CREATE TABLE penalty_tiers (
    tier_id     INT AUTO_INCREMENT PRIMARY KEY,
    tier_code   VARCHAR(20)  NOT NULL UNIQUE, -- 'NONE', 'WARN', 'HIDE', 'LOCK'
    tier_name   VARCHAR(50)  NOT NULL,        -- 'Không phạt', 'Cảnh cáo', 'Ẩn món/quán', 'Khóa quán'
    description VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL
) ENGINE=InnoDB;

-- ============================================================
-- 11. BẢNG KHUNG THỜI GIAN GIAO HÀNG (DELIVERY_TIME_FRAMES)
-- Danh mục khung thời gian giao hàng dự kiến của quán
-- ============================================================
CREATE TABLE delivery_time_frames (
    frame_id     INT AUTO_INCREMENT PRIMARY KEY,
    frame_code   VARCHAR(20)  NOT NULL UNIQUE, -- '15-25MIN', '20-30MIN', '25-35MIN', '30-45MIN'
    display_text VARCHAR(50)  NOT NULL,        -- '15-25 phút', '20-30 phút', '25-35 phút', '30-45 phút'
    min_minutes  INT          NOT NULL,
    max_minutes  INT          NOT NULL,
    is_active    BOOLEAN      DEFAULT TRUE NOT NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL
) ENGINE=InnoDB;

-- ============================================================
-- 12. BẢNG CỬA HÀNG / GIAN HÀNG QUÁN ĂN (STORES)
-- Giao diện: Quán/5.html — Dashboard quản lý gian hàng
-- Liên kết khóa ngoại: store_statuses, penalty_tiers, delivery_time_frames
-- ============================================================
CREATE TABLE stores (
    store_id                BIGINT PRIMARY KEY,
    merchant_id             BIGINT       NOT NULL,
    store_name              VARCHAR(150) NOT NULL,
    store_phone             VARCHAR(15)  NOT NULL,
    address                 VARCHAR(255) NOT NULL,
    latitude                DECIMAL(10,8),
    longitude               DECIMAL(11,8),
    image_url               TEXT,
    status_id               INT          DEFAULT 1 NOT NULL,
    penalty_tier_id         INT          DEFAULT 1 NOT NULL,
    penalty_reason          TEXT,
    rating_avg              DECIMAL(3,2) DEFAULT 5.00 NOT NULL,
    delivery_time_frame_id  INT          DEFAULT 2 NOT NULL,
    created_at              DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (merchant_id) REFERENCES merchants(user_id) ON DELETE CASCADE,
    FOREIGN KEY (status_id) REFERENCES store_statuses(status_id) ON UPDATE CASCADE,
    FOREIGN KEY (penalty_tier_id) REFERENCES penalty_tiers(tier_id) ON UPDATE CASCADE,
    FOREIGN KEY (delivery_time_frame_id) REFERENCES delivery_time_frames(frame_id) ON UPDATE CASCADE,
    INDEX idx_stores_merchant (merchant_id),
    INDEX idx_stores_status (status_id)
) ENGINE=InnoDB;

-- ============================================================
-- 11. BẢNG MÓN ĂN / THỰC ĐƠN (DISHES)
-- Giao diện: khách/b5.html — Chi tiết menu quán
-- ============================================================
CREATE TABLE dishes (
    dish_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id     BIGINT       NOT NULL,
    category_id  INT,
    dish_name    VARCHAR(150) NOT NULL,
    description  TEXT,
    image_url    VARCHAR(255),
    base_price   DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (base_price >= 0),
    is_available BOOLEAN      DEFAULT TRUE NOT NULL,
    is_hidden    BOOLEAN      DEFAULT FALSE NOT NULL,
    sold_count   INT          DEFAULT 0 NOT NULL,
    rating_avg   DECIMAL(3,2) DEFAULT 5.00 NOT NULL,
    review_count INT          DEFAULT 0 NOT NULL,
    FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE SET NULL,
    INDEX idx_dishes_store_active (store_id, is_available, is_hidden)
) ENGINE=InnoDB;

-- ============================================================
-- 7. BẢNG GÓI COMBO KHUYẾN MÃI (COMBOS)
-- Giao diện: Quán/3.html — Tạo gói Combo món ăn
-- ============================================================
CREATE TABLE combos (
    combo_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id    BIGINT       NOT NULL,
    combo_name  VARCHAR(150) NOT NULL,
    description TEXT,
    combo_price DECIMAL(12,0) NOT NULL CHECK (combo_price > 0),
    image_url   VARCHAR(255),
    is_active   BOOLEAN      DEFAULT TRUE NOT NULL,
    FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE,
    INDEX idx_combos_store (store_id)
) ENGINE=InnoDB;

-- ============================================================
-- 9. BẢNG CHI TIẾT COMBO (COMBO_ITEMS)
-- Bảng trung gian N-N giữa combos và dishes
-- ============================================================
CREATE TABLE combo_items (
    combo_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    combo_id      BIGINT NOT NULL,
    dish_id       BIGINT NOT NULL,
    quantity      INT    DEFAULT 1 NOT NULL CHECK (quantity > 0),
    FOREIGN KEY (combo_id) REFERENCES combos(combo_id) ON DELETE CASCADE,
    FOREIGN KEY (dish_id) REFERENCES dishes(dish_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 10. BẢNG TÀI XẾ GIAO HÀNG (DRIVERS)
-- Giao diện: Quán/1.html — Quản lý tài xế nội bộ quán
--            ship/2.html — Vào ca trực tuyến / Đón đơn
-- ============================================================
CREATE TABLE drivers (
    user_id        BIGINT PRIMARY KEY,
    store_id       BIGINT       DEFAULT NULL COMMENT 'NULL nếu tài xế độc lập, có ID nếu thuộc quán',
    driver_code    VARCHAR(20)  NOT NULL UNIQUE COMMENT 'VD: TX-19, TX-14, TX-08',
    cccd_number    VARCHAR(20)  NOT NULL COMMENT 'Số CCCD tài xế',
    cccd_front_url VARCHAR(255) DEFAULT NULL COMMENT 'Ảnh mặt trước CCCD',
    cccd_back_url  VARCHAR(255) DEFAULT NULL COMMENT 'Ảnh mặt sau CCCD',
    license_number VARCHAR(30)  DEFAULT NULL COMMENT 'Số GPLX A1/A2 của tài xế',
    vehicle_type   VARCHAR(50)  DEFAULT 'MOTORBIKE' NOT NULL COMMENT 'Xe máy số, tay ga, xe điện',
    vehicle_plate  VARCHAR(20)  NOT NULL COMMENT 'Biển kiểm soát xe',
    rating_avg     DECIMAL(3,2) DEFAULT 5.00 NOT NULL,
    duty_status    ENUM('AVAILABLE', 'DELIVERING', 'OFF_DUTY') DEFAULT 'OFF_DUTY' NOT NULL,
    current_lat    DECIMAL(10,8),
    current_lng    DECIMAL(11,8),
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE SET NULL,
    INDEX idx_drivers_store (store_id),
    INDEX idx_drivers_duty (duty_status)
) ENGINE=InnoDB;

-- ============================================================
-- 11. BẢNG CẤU HÌNH PHÍ SHIP TP.HCM (SHIPPING_RULES)
-- Giao diện: khách/b8.html — Phân vùng cước ship
-- ============================================================
CREATE TABLE shipping_rules (
    rule_id        INT AUTO_INCREMENT PRIMARY KEY,
    zone_type      ENUM('INTRA_DISTRICT', 'INTER_DISTRICT', 'SUBURBAN') NOT NULL,
    zone_name      VARCHAR(100) NOT NULL,
    fee_amount     DECIMAL(12,0) NOT NULL,
    max_distance_km DECIMAL(4,1) NOT NULL,
    is_active      BOOLEAN DEFAULT TRUE NOT NULL
) ENGINE=InnoDB;

-- ============================================================
-- 12. BẢNG ĐƠN HÀNG (ORDERS)
-- Giao diện: khách/b9.html — Theo dõi đơn + Mã QR Token
--            Quán/4.html — Tiếp nhận & Duyệt đơn
--            ship/4-6.html — GPS giao hàng
-- Vòng đời: PENDING → PREPARING → ASSIGNED → DELIVERING → COMPLETED / BOOM / CANCELLED
-- ============================================================
CREATE TABLE orders (
    order_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id      BIGINT       NOT NULL,
    store_id         BIGINT       NOT NULL,
    driver_id        BIGINT,
    delivery_address TEXT         NOT NULL,
    subtotal_amount  DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (subtotal_amount >= 0),
    shipping_fee     DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    total_payment    DECIMAL(12,0) NOT NULL DEFAULT 0,
    payment_method   ENUM('COD', 'MOMO_QR', 'BANKING') NOT NULL DEFAULT 'COD',
    order_status     ENUM('PENDING', 'PREPARING', 'ASSIGNED', 'DELIVERING', 'COMPLETED', 'BOOM', 'CANCELLED') DEFAULT 'PENDING' NOT NULL,
    delivery_token   VARCHAR(32)  NOT NULL UNIQUE COMMENT 'Mã QR bảo mật giao hàng',
    note             TEXT,
    created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at     DATETIME,
    FOREIGN KEY (customer_id) REFERENCES customers(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE RESTRICT,
    FOREIGN KEY (driver_id) REFERENCES drivers(user_id) ON DELETE SET NULL,
    INDEX idx_orders_customer_created (customer_id, created_at DESC),
    INDEX idx_orders_store_status (store_id, order_status),
    INDEX idx_orders_driver_status (driver_id, order_status),
    UNIQUE INDEX idx_orders_delivery_token (delivery_token)
) ENGINE=InnoDB;

-- ============================================================
-- 12. BẢNG CHI TIẾT ĐƠN HÀNG (ORDER_ITEMS)
-- Giao diện: khách/b7.html — Giỏ hàng → Chốt đơn
-- Lưu vết đơn giá món tại thời điểm đặt
-- ============================================================
CREATE TABLE order_items (
    order_item_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    dish_id         BIGINT       NOT NULL,
    quantity        INT          NOT NULL CHECK (quantity > 0),
    unit_price      DECIMAL(12,0) NOT NULL,
    item_subtotal   DECIMAL(12,0) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (dish_id) REFERENCES dishes(dish_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 14. BẢNG KHIẾU NẠI (COMPLAINTS)
-- Giao diện: khách/b12.html — Gửi khiếu nại đơn hàng
--            admin.html — Xử lý khiếu nại 3 mức phạt
-- ============================================================
CREATE TABLE complaints (
    complaint_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    customer_id     BIGINT       NOT NULL,
    store_id        BIGINT       NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    evidence_image  VARCHAR(255),
    status          ENUM('PENDING', 'RESOLVED', 'REJECTED') DEFAULT 'PENDING' NOT NULL,
    penalty_applied ENUM('NONE', 'WARN', 'HIDE', 'LOCK') DEFAULT 'NONE' NOT NULL,
    admin_note      TEXT,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES customers(user_id) ON DELETE CASCADE,
    FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE,
    INDEX idx_complaints_status (status, created_at DESC)
) ENGINE=InnoDB;

-- ============================================================
-- 15. BẢNG THÔNG BÁO (NOTIFICATIONS)
-- Giao diện: khách/b10.html — Hộp thư thông báo
-- ============================================================
CREATE TABLE notifications (
    notification_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    title             VARCHAR(150) NOT NULL,
    content           TEXT         NOT NULL,
    notification_type ENUM('ORDER_UPDATE', 'TRUST_SCORE', 'WARNING', 'SYSTEM') NOT NULL,
    is_read           BOOLEAN      DEFAULT FALSE NOT NULL,
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_notif_user (user_id, is_read)
) ENGINE=InnoDB;

-- ============================================================
-- 16. BẢNG ĐÁNH GIÁ MÓN ĂN (DISH_REVIEWS)
-- Giao diện: Đánh giá món ăn sau khi hoàn tất đơn hàng
-- ============================================================
CREATE TABLE dish_reviews (
    review_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    dish_id         BIGINT       NOT NULL,
    customer_id     BIGINT       NOT NULL,
    rating          INT          NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         TEXT,
    image_url       VARCHAR(255),
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (dish_id) REFERENCES dishes(dish_id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES customers(user_id) ON DELETE CASCADE,
    INDEX idx_reviews_dish (dish_id),
    INDEX idx_reviews_customer (customer_id)
) ENGINE=InnoDB;

-- ============================================================
-- GIỎ HÀNG TẠM (CARTS & CART_ITEMS)
-- Giao diện: khách/b7.html — Giỏ hàng quán
--            khách/a1.html — Giỏ hàng đa quán All-in-One
-- ============================================================
CREATE TABLE carts (
    cart_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id    BIGINT NOT NULL,
    store_id       BIGINT NOT NULL,
    total_amount   DECIMAL(12,0) DEFAULT 0 NOT NULL,
    updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(user_id) ON DELETE CASCADE,
    FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE,
    UNIQUE INDEX idx_cart_unique (customer_id, store_id)
) ENGINE=InnoDB;

CREATE TABLE cart_items (
    cart_item_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id         BIGINT       NOT NULL,
    dish_id         BIGINT       NOT NULL,
    quantity        INT          DEFAULT 1 NOT NULL,
    selected_options JSON COMMENT 'Tùy chọn Size/Đường/Đá/Topping',
    FOREIGN KEY (cart_id) REFERENCES carts(cart_id) ON DELETE CASCADE,
    FOREIGN KEY (dish_id) REFERENCES dishes(dish_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- NẠP DỮ LIỆU MẪU CHO HỆ THỐNG 4 ACTORS
-- ============================================================

-- 0. Roles & User Statuses (Vai trò & Trạng thái hệ thống)
INSERT INTO roles (role_id, role_code, role_name, description) VALUES
(1, 'ADMIN',    'Quản trị viên',   'Toàn quyền quản trị hệ thống, kiểm duyệt quán và xử lý khiếu nại'),
(2, 'CUSTOMER', 'Khách hàng',      'Người dùng mua đồ ăn, tạo giỏ hàng, đặt đơn và đánh giá món ăn'),
(3, 'MERCHANT', 'Chủ cửa hàng',    'Quản lý thực đơn, duyệt đơn hàng và điều phối tài xế nội bộ'),
(4, 'DRIVER',   'Tài xế giao hàng', 'Tài xế nội bộ của quán, nhận đơn, định vị GPS và xác thực QR');

INSERT INTO user_statuses (status_id, status_code, status_name, description) VALUES
(1, 'ACTIVE',   'Đang hoạt động',   'Tài khoản đang trực tuyến và có thể tương tác bình thường'),
(2, 'INACTIVE', 'Ngưng hoạt động', 'Tài khoản tạm ngừng hoạt động hoặc chưa kích hoạt'),
(3, 'OFFLINE',  'Ngoại tuyến',      'Tài khoản đã đăng xuất hoặc ở trạng thái nghỉ ca');

-- 1. Users (4 Actors liên kết role_id & status_id)
INSERT INTO users (user_id, role_id, status_id, phone_number, password_hash, full_name, email) VALUES
(1, 1, 1, '0900000001', '$2b$12$admin_hash_placeholder', 'Admin Oishi Food', 'admin@oishifood.vn'),
(2, 2, 1, '0976543210', '$2b$12$customer_hash_placeholder', 'An Nguyễn (Khách Hàng)', 'customer@oishifood.vn'),
(3, 3, 1, '0908123456', '$2b$12$merchant_hash_placeholder', 'Hồng Trà Ngô Gia (Chủ Quán)', 'merchant@oishifood.vn'),
(4, 4, 1, '0912345678', '$2b$12$driver1_hash_placeholder', 'Nguyễn Văn Hùng (TX-19)', 'driver1@oishifood.vn'),
(5, 4, 3, '0934567890', '$2b$12$driver2_hash_placeholder', 'Trần Minh Tâm (TX-14)', 'driver2@oishifood.vn'),
(6, 4, 1, '0945678901', '$2b$12$driver3_hash_placeholder', 'Lê Hoàng Phúc (TX-08)', 'driver3@oishifood.vn'),
(7, 3, 1, '0956789012', '$2b$12$merchant2_hash_placeholder', 'Cơm Tấm Sài Gòn (Chủ Quán)', 'merchant2@oishifood.vn'),
(8, 3, 2, '0967890123', '$2b$12$merchant3_hash_placeholder', 'Phở Hà Nội 36 (Chủ Quán)', 'merchant3@oishifood.vn');

-- 2. Customers (Hồ sơ khách hàng với Trust Score)
INSERT INTO customers (user_id, trust_score, total_orders, completed_orders, boom_orders) VALUES
(2, 98, 47, 46, 1);

-- 4. Customer Addresses
INSERT INTO customer_addresses (user_id, receiver_name, receiver_phone, street_address, latitude, longitude, is_default) VALUES
(2, 'An Nguyễn', '0976543210', '88 Đồng Khởi, P. Bến Nghé, Q.1, TP.HCM', 10.7769440, 106.7009680, TRUE),
(2, 'An Nguyễn (Nhà)', '0976543210', '215 Điện Biên Phủ, P.15, Q. Bình Thạnh, TP.HCM', 10.8003520, 106.7131410, FALSE);

-- 5. Categories (Danh mục ShopeeFood-style)
INSERT INTO categories (category_name, icon_url, display_order) VALUES
('Cơm Trưa', '🍚', 1),
('Trà Sữa', '🧋', 2),
('Bún/Phở', '🍜', 3),
('Gà Rán', '🍗', 4),
('Bánh Mì', '🥖', 5),
('Lẩu', '🍲', 6),
('Đồ Chay', '🥗', 7),
('Tráng Miệng', '🍰', 8);

-- 5. Approval Statuses (Trạng thái xét duyệt hồ sơ đối tác)
INSERT INTO approval_statuses (status_id, status_code, status_name, description) VALUES
(1, 'PENDING',  'Chờ duyệt', 'Hồ sơ mới gửi, đang chờ quản trị viên đối chiếu CCCD và cấp phép'),
(2, 'APPROVED', 'Đã duyệt',  'Hồ sơ hợp lệ, đã được cấp quyền kinh doanh/giao hàng trên nền tảng'),
(3, 'REJECTED', 'Từ chối',   'Hồ sơ không hợp lệ hoặc thông tin CCCD không trùng khớp');

-- 6. Merchants (Hồ sơ chủ quán đối tác — 4 ảnh CCCD)
INSERT INTO merchants (user_id, cccd_number, cccd_front_url, cccd_back_url, cccd_hold_url, cccd_portrait_url, approval_status_id) VALUES
(3, '079203012345', 'https://example.com/cccd_front_3.jpg', 'https://example.com/cccd_back_3.jpg', 'https://example.com/cccd_hold_3.jpg', 'https://example.com/cccd_portrait_3.jpg', 2),
(7, '079203054321', 'https://example.com/cccd_front_7.jpg', 'https://example.com/cccd_back_7.jpg', 'https://example.com/cccd_hold_7.jpg', 'https://example.com/cccd_portrait_7.jpg', 2),
(8, '079203099999', 'https://example.com/cccd_front_8.jpg', 'https://example.com/cccd_back_8.jpg', 'https://example.com/cccd_hold_8.jpg', 'https://example.com/cccd_portrait_8.jpg', 1);

-- 7. Bank Accounts (Tài khoản ngân hàng thụ hưởng đối tác)
INSERT INTO bank_accounts (user_id, bank_name, account_number, account_holder_name, branch_name, is_default) VALUES
(3, 'Vietcombank', '0071001234567', 'NGUYEN HOANG PHUC', 'Chi nhánh Tân Bình', TRUE),
(7, 'Techcombank', '1903678901234', 'TRAN MINH TAM', 'Chi nhánh Quận 1', TRUE),
(8, 'MB Bank', '0967890123', 'LE HOANG PHUC', 'Chi nhánh Bến Thành', TRUE);

-- 8. Store Statuses (Trạng thái hoạt động gian hàng)
INSERT INTO store_statuses (status_id, status_code, status_name, description) VALUES
(1, 'PENDING', 'Chờ duyệt',    'Gian hàng đang đợi Admin phê duyệt hồ sơ'),
(2, 'OPEN',    'Đang mở cửa',  'Gian hàng đang nhận đơn đặt từ khách hàng'),
(3, 'CLOSED',  'Đang đóng cửa','Gian hàng tạm nghỉ hoặc hết giờ mở cửa'),
(4, 'LOCKED',  'Bị khóa',      'Gian hàng bị khóa do chế tài vi phạm');

-- 9. Penalty Tiers (Cấp độ chế tài xử phạt quán ăn)
INSERT INTO penalty_tiers (tier_id, tier_code, tier_name, description) VALUES
(1, 'NONE', 'Không phạt',   'Gian hàng hoạt động bình thường, không có vi phạm'),
(2, 'WARN', 'Cảnh cáo',     'Nhắc nhở qua thông báo hệ thống về chất lượng dịch vụ'),
(3, 'HIDE', 'Ẩn món/quán',  'Tạm ẩn gian hàng khỏi trang tìm kiếm 24h'),
(4, 'LOCK', 'Khóa quán',    'Khóa hoạt động kinh doanh vĩnh viễn hoặc đến khi khắc phục');

-- 10. Delivery Time Frames (Khung thời gian giao hàng dự kiến)
INSERT INTO delivery_time_frames (frame_id, frame_code, display_text, min_minutes, max_minutes) VALUES
(1, '15-25MIN', '15-25 phút', 15, 25),
(2, '20-30MIN', '20-30 phút', 20, 30),
(3, '25-35MIN', '25-35 phút', 25, 35),
(4, '30-45MIN', '30-45 phút', 30, 45);

-- 11. Stores (Gian hàng quán ăn của đối tác)
INSERT INTO stores (store_id, merchant_id, store_name, store_phone, address, image_url, status_id, penalty_tier_id, rating_avg, delivery_time_frame_id) VALUES
(3, 3, 'Hồng Trà Ngô Gia', '0908123456', '321 Cách Mạng Tháng 8, P.12, Q.10', 'https://images.unsplash.com/photo-1558857563-b371033873b8?w=500', 2, 1, 4.8, 1),
(7, 7, 'Cơm Tấm Sài Gòn', '0956789012', '123 Nguyễn Trãi, P. Bến Thành, Q.1', 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=500', 2, 1, 4.9, 2),
(8, 8, 'Phở Hà Nội 36', '0967890123', '456 Lê Lợi, P. Bến Nghé, Q.1', 'https://images.unsplash.com/photo-1555126634-323283e090fa?w=500', 1, 1, 5.00, 3);

-- 8. Drivers (Tài xế giao hàng — Actor DRIVER)
INSERT INTO drivers (user_id, store_id, driver_code, cccd_number, cccd_front_url, cccd_back_url, license_number, vehicle_type, vehicle_plate, rating_avg, duty_status) VALUES
(4, 3, 'TX-19', '079203011111', 'https://example.com/driver4_cccd_f.jpg', 'https://example.com/driver4_cccd_b.jpg', 'GPLX-79012345', 'MOTORBIKE', '59H1-12345', 4.9, 'AVAILABLE'),
(5, 3, 'TX-14', '079203022222', 'https://example.com/driver5_cccd_f.jpg', 'https://example.com/driver5_cccd_b.jpg', 'GPLX-79067890', 'MOTORBIKE', '59H1-67890', 4.7, 'OFF_DUTY'),
(6, 7, 'TX-08', '079203033333', 'https://example.com/driver6_cccd_f.jpg', 'https://example.com/driver6_cccd_b.jpg', 'GPLX-79011111', 'MOTORBIKE', '59H1-11111', 4.8, 'AVAILABLE');

-- 7. Dishes (Thực đơn quán)
INSERT INTO dishes (store_id, category_id, dish_name, description, image_url, base_price) VALUES
(3, 2, 'Trà sữa trân châu hoàng kim', 'Vị trà đen đậm đà hòa cùng sữa tươi thơm béo và trân châu dẻo dai', 'https://images.unsplash.com/photo-1558857563-b371033873b8?w=500', 42000),
(3, 2, 'Hồng trà chanh leo', 'Hồng trà thơm mát kết hợp chanh leo chua ngọt tự nhiên', 'https://images.unsplash.com/photo-1556679343-c1d6e9d4a1d8?w=500', 38000),
(3, 2, 'Matcha latte kem phô mai', 'Matcha Nhật Bản hoà quyện cùng sữa tươi và kem phô mai béo ngậy', 'https://images.unsplash.com/photo-1515823064-d6e0c04616a7?w=500', 52000),
(7, 1, 'Cơm sườn nướng than hoa', 'Cơm tấm sườn nướng than hoa mềm thơm kèm mỡ hành, đồ chua', 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=500', 55000),
(7, 1, 'Cơm sườn bì chả đặc biệt', 'Cơm tấm kèm sườn bì chả, trứng ốp la, chén mắm pha tỏi ớt', 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500', 65000),
(8, 3, 'Phở bò tái lăn Hà Nội', 'Bò tái lăn đảo trên chảo lửa rực, nước dùng ngọt xương thanh tao', 'https://images.unsplash.com/photo-1555126634-323283e090fa?w=500', 65000);

-- 8. Combos (Gói combo khuyến mãi)
INSERT INTO combos (store_id, combo_name, description, combo_price) VALUES
(3, 'Combo Đôi Trà Sữa', '2 Trà sữa trân châu hoàng kim + 1 Hồng trà chanh leo', 99000),
(7, 'Combo Tiết Kiệm Trưa', '1 Cơm sườn nướng + 1 Canh chua + 1 Nước ngọt', 75000);

-- 9. Combo Items
INSERT INTO combo_items (combo_id, dish_id, quantity) VALUES
(1, 1, 2),
(1, 2, 1),
(2, 4, 1);

-- 10. Shipping Rules (Biểu phí ship TP.HCM)
INSERT INTO shipping_rules (zone_type, zone_name, fee_amount, max_distance_km) VALUES
('INTRA_DISTRICT', 'Nội quận (cùng quận)', 15000, 3.0),
('INTER_DISTRICT', 'Liên quận trung tâm', 25000, 7.0),
('SUBURBAN', 'Ngoại thành TP.HCM', 45000, 15.0);

-- 11. Orders (Đơn hàng mẫu — đầy đủ vòng đời)
INSERT INTO orders (customer_id, store_id, driver_id, delivery_address, subtotal_amount, shipping_fee, total_payment, payment_method, order_status, delivery_token, note) VALUES
(2, 3, NULL, '88 Đồng Khởi, P. Bến Nghé, Q.1, TP.HCM', 92000, 25000, 117000, 'MOMO_QR', 'PENDING', 'OISHI-TK-770001', 'Giao tận cửa phòng'),
(2, 7, 6, '215 Điện Biên Phủ, P.15, Q. Bình Thạnh, TP.HCM', 120000, 15000, 135000, 'COD', 'DELIVERING', 'OISHI-TK-770002', 'Cho nhiều nước mắm tỏi ớt'),
(2, 7, 6, '88 Đồng Khởi, P. Bến Nghé, Q.1, TP.HCM', 55000, 15000, 70000, 'BANKING', 'COMPLETED', 'OISHI-TK-770003', 'Đã nhận hàng thành công');

-- 12. Order Items
INSERT INTO order_items (order_id, dish_id, quantity, unit_price, item_subtotal) VALUES
(1, 1, 1, 50000, 50000),
(1, 2, 1, 42000, 42000),
(2, 4, 1, 55000, 55000),
(2, 5, 1, 65000, 65000),
(3, 4, 1, 55000, 55000);

-- 14. Complaints (Khiếu nại mẫu)
INSERT INTO complaints (order_id, customer_id, store_id, reason, status, penalty_applied, admin_note) VALUES
(3, 2, 7, 'Giao thiếu nước mắm tỏi ớt, đồ ăn nguội', 'RESOLVED', 'WARN', 'Đã cảnh cáo quán lần 1, yêu cầu cải thiện chất lượng');

-- 15. Notifications
INSERT INTO notifications (user_id, title, content, notification_type) VALUES
(2, '🛵 Đơn hàng đang giao!', 'Tài xế TX-08 (Lê Hoàng Phúc) đang trên đường giao đơn #2 của bạn.', 'ORDER_UPDATE'),
(2, '✅ Đơn hàng hoàn tất!', 'Đơn hàng #3 tại Cơm Tấm Sài Gòn đã giao thành công.', 'ORDER_UPDATE'),
(2, '⭐ Điểm uy tín cập nhật', 'Điểm uy tín của bạn: 98/100 (Hạng Gold). Cảm ơn bạn đã tin tưởng!', 'TRUST_SCORE'),
(3, '⚠️ Cảnh cáo vi phạm', 'Quán bạn bị khiếu nại về đơn #3. Mức xử lý: Cảnh cáo lần 1.', 'WARNING');

-- 16. Dish Reviews (Đánh giá món ăn mẫu)
INSERT INTO dish_reviews (order_id, dish_id, customer_id, rating, comment) VALUES
(3, 4, 2, 5, 'Cơm sườn nướng rất thơm mềm, mỡ hành béo ngậy, giao hàng nhanh 5 sao!'),
(2, 4, 2, 4, 'Thịt ướp vừa miệng, đóng gói sạch sẽ cẩn thận.');

-- ============================================================
-- 17. BẢNG LỊCH SỬ BIẾN ĐỘNG ĐIỂM UY TÍN (TRUST_SCORE_LOGS)
-- Lưu vết nhật ký lịch sử cộng/trừ điểm uy tín khách hàng
-- ============================================================
CREATE TABLE trust_score_logs (
    log_id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id   BIGINT       NOT NULL,
    order_id      BIGINT       NULL,
    change_amount INT          NOT NULL COMMENT 'Số điểm thay đổi, VD: +1 hoặc reset 0',
    old_score     INT          NOT NULL COMMENT 'Điểm trước biến động',
    new_score     INT          NOT NULL COMMENT 'Điểm sau biến động',
    reason        VARCHAR(255) NOT NULL COMMENT 'Lý do: Hoàn thành đơn (+1), Bom hàng (Reset 0)',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(user_id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE SET NULL
) ENGINE=InnoDB;

INSERT INTO trust_score_logs (customer_id, order_id, change_amount, old_score, new_score, reason) VALUES
(2, 3, 1, 45, 46, 'Hoàn thành đơn hàng #3 thành công');

