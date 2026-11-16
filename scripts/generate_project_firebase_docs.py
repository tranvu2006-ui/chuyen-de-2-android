# -*- coding: utf-8 -*-
"""
Script sinh tài liệu giải thích chi tiết toàn bộ dự án Oishi Food & các Node Firebase Realtime Database.
Xuất ra 2 file tại thư mục gốc dự án:
  1. GIAI_THICH_CHI_TIET_DU_AN_VA_FIREBASE.xlsx
  2. GIAI_THICH_CHI_TIET_DU_AN_VA_FIREBASE.docx
"""

import os
import sys
import math
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

if sys.platform.startswith('win'):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
XLSX_PATH = os.path.join(ROOT_DIR, "GIAI_THICH_CHI_TIET_DU_AN_VA_FIREBASE.xlsx")
DOCX_PATH = os.path.join(ROOT_DIR, "GIAI_THICH_CHI_TIET_DU_AN_VA_FIREBASE.docx")

# ==============================================================================
# DỮ LIỆU CHUẨN HÓA TỪ MÃ NGUỒN DỰ ÁN OISHI FOOD
# ==============================================================================

ARCHITECTURE_OVERVIEW = [
    ("Tên hệ thống", "Oishi Food — Hệ thống Đặt & Giao Đồ Ăn Trực Tuyến Đa Vai Trò (Chuyên Đề Tốt Nghiệp 2)"),
    ("Ngôn ngữ & Nền tảng", "Kotlin (JVM 17, Android compileSdk 35, minSdk 26, targetSdk 35), HTML5/CSS3/JS (Admin Portal & Leaflet Maps)"),
    ("Cơ sở dữ liệu Realtime", "Firebase Realtime Database (asia-southeast1) + Firebase Authentication + Firebase Cloud Messaging (FCM)"),
    ("Xác thực & Dịch vụ ngoài", "Supabase Auth REST API (gửi & xác thực mã OTP 6 số qua Gmail), VietQR API (api.vietqr.io), ML Kit OCR (quét CCCD), Osmdroid / Google Maps & Leaflet.js"),
    ("Kiến trúc phần mềm", "Multi-module Clean MVVM + Hilt Dependency Injection + Kotlin Coroutines & Flow (callbackFlow realtime) + ViewBinding"),
    ("Cấu trúc 4 Modules Gradle", "1) :core (Thư viện dùng chung) | 2) :app-customer (App Khách hàng) | 3) :app-internal (App Nội bộ Quán/Boss/Bếp/Shipper) | 4) :app-admin (App Quản trị viên)"),
    ("Web Portal đi kèm", "admin.html (Bảng điều khiển Admin Web chạy trên trình duyệt, đồng bộ Realtime trực tiếp với Firebase)"),
    ("Công cụ vận hành phụ trợ", "reset_database.bat + scripts/reset_firebase_data.py (dọn sạch dữ liệu test giữ lại admin@gmail.com) | start-admin.bat | repomix CODE_MAP.md")
]

MODULES_SUMMARY = [
    (":core", "com.chuyen_de_2.foodorder.core", "Thư viện nền tảng dùng chung cho cả 3 app Android",
     "Chứa 28 Data Models (@IgnoreExtraProperties), AuthRepository, FirebaseRepository (~3.858 dòng), EmailOtpApi, AccountLockWatcher, AppConstants, CccdOcrHelper, MomoPaymentGatewayClient, BankData (54 ngân hàng VietQR), QRCodeUtils, GoogleMapUtils."),
    (":app-customer", "com.chuyen_de_2.foodorder.customer", "Ứng dụng dành cho Khách hàng (CUSTOMER)",
     "Đăng ký/đăng nhập + OTP Gmail, duyệt quán theo danh mục/vị trí/bán chạy, tùy biến món (Size/Đường/Đá/Topping), giỏ hàng đồng bộ realtime, Checkout (COD/VietQR/Đặt cọc 20%/MoMo), theo dõi đơn & GPS tài xế realtime, chat với quán, quét/hiện mã QR nhận đơn, đánh giá món & gửi khiếu nại."),
    (":app-internal", "com.chuyen_de_2.foodorder.internal", "Ứng dụng Nội bộ (BOSSMERCHANT, MERCHANT, CHEF, DRIVER)",
     "Tích hợp 4 vai trò nội bộ: Chủ chuỗi (quản lý nhiều chi nhánh, phân công quản lý), Quản lý quán (Kanban duyệt đơn, đóng/mở quán, menu, báo cáo, gán bếp/tài xế, BankNotificationListenerService tự động duyệt tiền VietQR 24/7), Đầu bếp (nhận nấu đơn PREPARING->READY), Tài xế nội bộ (chuyến giao 4 bước GPS liên tục, LocationForegroundService, đếm ngược bom hàng, chứng cứ gọi/nhắn)."),
    (":app-admin", "com.chuyen_de_2.foodorder.admin", "Ứng dụng Quản trị viên Sàn (ADMIN)",
     "Dashboard tổng quan doanh thu/đơn hàng, quản lý & duyệt hồ sơ KYC CCCD 4 ảnh của Quán/Chủ chuỗi, quản lý danh mục món toàn sàn (tự động đẩy vị trí displayOrder), xử lý khiếu nại & áp chế tài 3 mức (WARN/HIDE/LOCK) hoặc phạt ngược báo cáo sai, giám sát Users/Orders & chỉnh sửa cấu hình đếm ngược sự cố.")
]

ROLES_OVERVIEW = [
    ("ADMIN", "1", ":app-admin & admin.html", "Quản trị viên Sàn",
     "Toàn quyền vận hành sàn: Duyệt KYC quán mới (CCCD 4 ảnh), Khóa/Mở khóa mọi tài khoản realtime (<500ms qua AccountLockWatcher), Quản lý danh mục toàn sàn, Phán quyết khiếu nại & chế tài, Cấu hình đếm ngược sự cố, Giám sát toàn bộ đơn hàng và điểm uy tín. Ngoại lệ miễn OTP khi đăng nhập admin@gmail.com."),
    ("CUSTOMER", "2", ":app-customer", "Khách hàng",
     "Đặt đồ ăn, lưu sổ địa chỉ, tùy biến món ăn, thanh toán linh hoạt. Sở hữu Điểm Uy Tín (mặc định 100đ): >=95đ Hạng Kim Cương (COD + giảm 5.000đ ship), >=90đ Hạng Vàng (được COD), <90đ bị khóa COD (phải Cọc 20% hoặc Chuyển khoản), bom hàng trừ -30đ, hoàn thành đơn +2đ."),
    ("BOSSMERCHANT", "3", ":app-internal (BossDashboardActivity)", "Chủ chuỗi Cửa hàng",
     "Sở hữu nhiều chi nhánh quán (node boss_stores/{bossUid}), thực hiện KYC hồ sơ kinh doanh/CCCD/Tài khoản ngân hàng đối soát, tạo chi nhánh mới kèm ghim GPS bản đồ, tạo & phân công/khóa tài khoản Quản lý chi nhánh (MERCHANT)."),
    ("MERCHANT / MANAGER", "3", ":app-internal (DashboardActivity)", "Quản lý Chi nhánh Quán",
     "Điều hành 1 chi nhánh quán cụ thể: Bật/tắt trạng thái Đang bán (OPEN/CLOSED), quản lý thực đơn món & danh mục quán, nhận & duyệt đơn trên Kanban Board, điều phối Đầu bếp & Tài xế nội bộ, chat với khách, tự động xác nhận VietQR qua BankNotificationListenerService."),
    ("CHEF", "5 (CHEF)", ":app-internal (ChefFragment)", "Đầu bếp của Quán",
     "Thuộc một chi nhánh quán (node chefs/{chefId}): Xem hàng đợi đơn mới (PENDING), bấm nhận chế biến (gán chefId -> chuyển trạng thái PREPARING), xác nhận nấu xong món (chuyển READY) để tài xế đến lấy."),
    ("DRIVER / SHIPPER", "4", ":app-internal (ShipperFragment & DeliveryTripActivity)", "Tài xế nội bộ Quán",
     "Thuộc biên chế quán (node drivers/{driverId}, mã TX-xx): Bật/tắt ca trực (AVAILABLE/DELIVERING/OFF_DUTY), nhận đơn giao, chạy LocationForegroundService cập nhật GPS realtime vào tracking/{orderId}, lộ trình 4 bước GPS trên WebView bản đồ, quét QR deliveryToken xác nhận giao xong, báo cáo sự cố/bom hàng 2 vòng đếm ngược.")
]

# Toàn bộ 23 nhóm Node trên Firebase Realtime Database (khớp database.rules.json & FirebaseRepository.kt)
FIREBASE_NODES = [
    (1, "users", "users/{uid}", "User.kt", "Người dùng & Phân quyền",
     "Node trung tâm lưu thông tin định danh tài khoản chung của mọi Actor (ADMIN, CUSTOMER, BOSSMERCHANT, MERCHANT, CHEF, DRIVER): tên, email, sđt, role, trạng thái khóa (status, isActive), storeId, bossId, fcmToken, trạng thái duyệt KYC.",
     "auth != null", "Chính chủ ($userId == auth.uid) hoặc ADMIN / BOSS / MERCHANT / DRIVER / CHEF",
     "role, roleId, status, email, phone, storeId, bossId, trustScore"),
    (2, "customers", "customers/{customerId}", "Customer.kt", "Người dùng & Phân quyền",
     "Lưu hồ sơ tín nhiệm riêng cho Khách hàng: Điểm uy tín (trustScore, mặc định 100), tổng đơn đặt (totalOrders), đơn hoàn thành (completedOrders), số lần bom hàng (boomOrders) để tự động tính hạng và điều kiện thanh toán COD.",
     "auth != null", "auth != null",
     "trustScore, status, email, phone"),
    (3, "customer_addresses", "customer_addresses/{userId}/{addressId}", "CustomerAddress.kt", "Người dùng & Vận hành Khách",
     "Sổ địa chỉ giao hàng đã lưu của từng khách hàng (Nhãn Nhà riêng/Công ty, tên & sđt người nhận, địa chỉ đầy đủ, quận, tọa độ GPS latitude/longitude, cờ isDefault).",
     "Chính chủ ($userId) hoặc ADMIN", "Chính chủ ($userId) hoặc ADMIN",
     "(Chưa đánh index phụ)"),
    (4, "merchants", "merchants/{merchantId}", "Merchant.kt", "Người dùng & Phân quyền",
     "Lưu hồ sơ pháp lý & KYC của Chủ quán/Đối tác: số CCCD, 4 ảnh CCCD (mặt trước, mặt sau, cầm CCCD, chân dung), giấy phép kinh doanh, mã số thuế, thông tin ngân hàng đối soát và trạng thái duyệt (PENDING/APPROVED/REJECTED).",
     "auth != null", "Chính chủ, ADMIN, BOSSMERCHANT, MERCHANT",
     "storeId, status, approvalStatus"),
    (5, "stores", "stores/{storeId}", "Store.kt", "Cửa hàng & Thực đơn",
     "Lưu thông tin chi nhánh quán ăn: tên quán, địa chỉ, tọa độ GPS (latitude/longitude), ảnh đại diện, giờ mở cửa, trạng thái quán (PENDING/OPEN/CLOSED/LOCKED), chế tài phạt (NONE/WARN/HIDE/LOCK), đánh giá trung bình, tài khoản VietQR/MoMo nhận tiền và map món ăn con (dishes).",
     "true (Công khai cho mọi app)", "auth != null",
     "merchantId, ownerId, bossId, storeStatus, status, category, isVerified, penaltyTier"),
    (6, "boss_stores", "boss_stores/{bossUid}/{storeId}", "Boolean (true) / Store mapping", "Cửa hàng & Phân cấp Chuỗi",
     "Bảng ánh xạ phân cấp 1-N giữa Chủ chuỗi (BOSSMERCHANT) và các chi nhánh quán (storeId) thuộc quyền sở hữu của Boss đó; đồng thời lưu chi nhánh đang chọn (activeStoreId) khi chuyển màn hình.",
     "Chính chủ ($bossUid) hoặc ADMIN", "Chính chủ ($bossUid) hoặc ADMIN",
     "(Key theo $bossUid)"),
    (7, "chefs", "chefs/{chefId}", "Chef.kt", "Nhân sự Nội bộ Quán",
     "Quản lý hồ sơ Đầu bếp nội bộ trực thuộc từng chi nhánh quán: mã bếp (chefId), mã quán (storeId), họ tên, sđt, email, chức danh, số đơn đang nấu (activeOrdersCount), trạng thái trực tuyến (isOnline).",
     "auth != null", "Chính chủ, ADMIN, BOSS, MERCHANT, CHEF",
     "storeId, email, phone"),
    (8, "drivers", "drivers/{driverId}", "Driver.kt", "Nhân sự Nội bộ Quán",
     "Quản lý đội ngũ Tài xế giao hàng nội bộ của quán: mã tài xế (driverCode: TX-xx), storeId, hồ sơ CCCD & GPLX, loại xe/biển số, trạng thái ca trực (dutyStatus: AVAILABLE/DELIVERING/OFF_DUTY), GPS hiện tại (currentLat/Lng), số đơn trong ngày & tích lũy.",
     "auth != null", "Chính chủ, ADMIN, BOSS, MERCHANT",
     "dutyStatus, storeId, email, phone, driverCode"),
    (9, "bank_accounts", "bank_accounts/{bankAccountId}", "BankAccount (SQL/Model ánh xạ)", "Tài chính & Đối soát",
     "Lưu thông tin tài khoản ngân hàng thụ hưởng (mã BIN ngân hàng, số tài khoản, tên chủ tài khoản) liên kết theo userId hoặc storeId phục vụ sinh mã VietQR và đối soát doanh thu.",
     "auth != null", "Chủ sở hữu (userId == auth.uid) hoặc ADMIN",
     "userId, storeId"),
    (10, "categories", "categories/{categoryId}", "Category.kt", "Cửa hàng & Thực đơn",
     "Danh mục ngành hàng món ăn toàn sàn (Cơm, Phở, Trà sữa, Bún đậu, Nước ép...) do Admin quản lý: iconUrl (URL hoặc Base64), thứ tự hiển thị (displayOrder tự động sắp xếp/chen vị trí), trạng thái kích hoạt (isActive).",
     "true (Công khai)", "auth != null",
     "displayOrder, isActive, status"),
    (11, "menus", "menus/{id} & menus/categories/{catId}", "MenuItem.kt / Category.kt", "Cửa hàng & Thực đơn",
     "Lưu thực đơn chuẩn hóa theo mô hình chung và đường dẫn dự phòng menus/categories dùng để tương thích ngược khi đọc danh mục cũ (có hàm cleanupOrphanedData dọn trùng trong FirebaseRepository).",
     "true (Công khai)", "ADMIN, BOSS, MERCHANT",
     "(Đọc trực tiếp theo nhánh con)"),
    (12, "combos", "combos/{comboId}", "Combo.kt", "Cửa hàng & Thực đơn",
     "Lưu các gói Combo món ăn khuyến mãi của quán: comboName, storeId, giá bán combo (comboPrice), ảnh, danh sách dishIds và chi tiết ComboItem (số lượng, đơn giá từng món trong combo).",
     "true (Công khai)", "ADMIN, BOSS, MERCHANT",
     "(Chưa đánh index phụ)"),
    (13, "restaurants", "restaurants/{id}", "Restaurant.kt", "Cửa hàng & Thực đơn",
     "Node tương thích kiến trúc nhà hàng/thương hiệu ban đầu (chứa id, name, address, imageUrl, rating, category, deliveryTime, isOpen), hiện tại app-customer đã chuẩn hóa đọc chính từ node stores.",
     "true (Công khai)", "ADMIN, BOSS, MERCHANT",
     "(Chưa đánh index phụ)"),
    (14, "carts", "carts/{userId}/{shopId}", "Cart.kt & CartItem.kt", "Đơn hàng & Giỏ hàng",
     "Lưu giỏ hàng tạm thời theo từng khách hàng và từng quán: tổng tiền tạm tính (totalAmount), thông tin quán, map các CartItem (mã món, tên, giá, số lượng, tùy chọn size/đường/đá/topping). Tự động xóa khi Checkout thành công.",
     "Chính chủ ($userId) hoặc ADMIN", "Chính chủ ($userId) hoặc ADMIN",
     "(Key theo $userId/$shopId)"),
    (15, "orders", "orders/{orderId}", "Order.kt & OrderItem", "Đơn hàng & Vận hành",
     "Node cốt lõi chứa toàn bộ đơn hàng của hệ thống: thông tin Khách/Quán/Bếp/Tài xế, danh sách món đặt (items), tiền hàng/ship/giảm giá/cọc 20%, phương thức & trạng thái thanh toán (PAID/UNPAID), vòng đời trạng thái đơn (PENDING->COMPLETED/BOOM/CANCELLED), mã QR deliveryToken và dữ liệu sự cố/hủy đơn.",
     "auth != null", "auth != null",
     "customerId, shipperId, chefId, driverId, shopId, storeId, status, orderStatus, timestamp, createdAt"),
    (16, "tracking", "tracking/{orderId} (và tracking/chats_{orderId})", "TrackingLocation.kt", "Định vị GPS & Realtime",
     "Lưu tọa độ GPS thời gian thực (lat, lng, updatedAt) được LocationForegroundService của Tài xế đẩy lên mỗi 3-5 giây theo mã đơn hàng thật để Khách hàng nhìn thấy icon xe di chuyển trực tiếp trên bản đồ. Đồng thời là kênh phụ lưu chat đơn hàng.",
     "auth != null", "auth != null",
     "(Key theo $orderId)"),
    (17, "order_chats", "order_chats/{orderId}/{messageId}", "Map Payload tin nhắn", "Giao tiếp Đơn hàng",
     "Kênh nhắn tin thời gian thực giữa Khách hàng và Quản lý Quán/Tài xế gắn với từng đơn hàng cụ thể (gửi văn bản, thời gian, vai trò người gửi), có cơ chế ghi/đọc song song với tracking/chats_{orderId}.",
     "auth != null", "auth != null",
     "(Key theo $orderId)"),
    (18, "dish_reviews", "dish_reviews/{reviewId}", "DishReview.kt", "Đánh giá & Phản hồi",
     "Lưu đánh giá món ăn của khách hàng sau khi đơn COMPLETED: điểm sao (rating 1-5), bình luận, ảnh chụp thực tế, tùy chọn món đã chọn (specText), số lượt thích hữu ích (likes). Tự động cập nhật ngược ratingAvg và reviewCount lên món và quán.",
     "true (Công khai)", "auth != null",
     "dishId, storeId, orderId, customerId, createdAt"),
    (19, "complaints", "complaints/{complaintId}", "Complaint.kt", "Khiếu nại & Kiểm soát",
     "Quản lý khiếu nại đơn hàng từ 3 nguồn (CUSTOMER, STORE, DRIVER) đối với vi phạm (ATTP, giao sai món, thái độ, giao trễ, bom hàng, gian lận): ảnh bằng chứng, trạng thái xử lý (PENDING/RESOLVED/REJECTED), mức chế tài (WARN/HIDE/LOCK) và phạt ngược nếu vu khống.",
     "auth != null", "auth != null",
     "customerId, storeId, orderId, status, createdAt"),
    (20, "trust_score_logs", "trust_score_logs/{logId}", "TrustScoreLog.kt", "Khiếu nại & Kiểm soát",
     "Nhật ký lịch sử biến động Điểm Uy Tín của khách hàng: ghi lại oldScore, newScore, changeAmount (ví dụ +2 khi nhận đơn thành công, -30 khi bom hàng COD), orderId liên quan, lý do và thời gian.",
     "auth != null", "auth != null",
     "customerId, orderId, createdAt"),
    (21, "system_config", "system_config/delivery_countdown", "DeliveryCountdownConfig.kt", "Cấu hình Hệ thống",
     "Lưu các cấu hình vận hành toàn sàn do Admin thiết lập, trọng tâm là delivery_countdown gồm round1Minutes (thời gian chờ khách vòng 1), round2Minutes (thời gian chờ gọi lại vòng 2) và updatedAt.",
     "true (Công khai)", "Chỉ ADMIN",
     "(Key cố định)"),
    (22, "delivery_time_frames", "delivery_time_frames/{1..4} & /countdown_config", "DeliveryCountdownConfig.kt", "Cấu hình Hệ thống",
     "Chứa 2 phần: (1) Danh mục 4 khung thời gian giao hàng dự kiến của quán (15-25 phút, 20-30 phút, 25-35 phút, 30-45 phút); (2) Bản sao đồng bộ (Dual-write/Fallback) của cấu hình đếm ngược countdown_config để đảm bảo app Tài xế luôn đọc được thời gian sự cố không bị lỗi quyền.",
     "true (Công khai)", "Chỉ ADMIN",
     "(Key cố định)"),
    (23, "Nhóm Node Từ điển & Thông báo (roles, user_statuses, approval_statuses, store_statuses, penalty_tiers, notifications)", "roles/{1..4}, user_statuses/{1..3}, approval_statuses/{1..3}, store_statuses/{1..4}, penalty_tiers/{1..4}, notifications/{id}", "Role.kt, UserStatus.kt, Notification.kt", "Từ điển Tham chiếu & Thông báo",
     "6 Node chuẩn hóa bảng danh mục tĩnh (tương ứng các bảng từ điển trong CSDL quan hệ) & hộp thư thông báo hệ thống: roles (ADMIN/CUSTOMER/MERCHANT/DRIVER), user_statuses (ACTIVE/INACTIVE/OFFLINE), approval_statuses (PENDING/APPROVED/REJECTED), store_statuses (PENDING/OPEN/CLOSED/LOCKED), penalty_tiers (NONE/WARN/HIDE/LOCK), notifications (thông báo đơn hàng, điểm uy tín, cảnh cáo).",
     "Từ điển: true | Toàn cục Root: Chỉ ADMIN", "Chỉ ADMIN",
     "(Danh mục tĩnh 1..4)")
]

# Chi tiết từng trường dữ liệu của các Node trọng tâm (khớp 100% Kotlin Models)
NODE_FIELDS_DETAIL = [
    # Node users (User.kt)
    ("users/{uid}", "User.kt", "uid", "String", "\"\"", "Mã định danh duy nhất của người dùng (trùng Firebase Auth UID)."),
    ("users/{uid}", "User.kt", "roleId", "Int", "2", "Mã số vai trò: 1=ADMIN, 2=CUSTOMER, 3=MERCHANT/BOSS, 4=DRIVER, 5=CHEF."),
    ("users/{uid}", "User.kt", "role", "String", "\"CUSTOMER\"", "Mã chữ vai trò RBAC: ADMIN, CUSTOMER, BOSSMERCHANT, MERCHANT, MANAGER, DRIVER, SHIPPER, CHEF."),
    ("users/{uid}", "User.kt", "name", "String", "\"\"", "Họ và tên hiển thị chung trên toàn hệ thống."),
    ("users/{uid}", "User.kt", "email", "String", "\"\"", "Địa chỉ email đăng nhập và nhận OTP xác thực."),
    ("users/{uid}", "User.kt", "phone", "String", "\"\"", "Số điện thoại liên hệ của người dùng."),
    ("users/{uid}", "User.kt", "statusId", "Int", "1", "Mã trạng thái tài khoản: 1=ACTIVE, 2=INACTIVE, 3=OFFLINE."),
    ("users/{uid}", "User.kt", "status", "String", "\"ACTIVE\"", "Trạng thái tài khoản: ACTIVE (bình thường), LOCKED (bị khóa), INACTIVE, OFFLINE."),
    ("users/{uid}", "User.kt", "isActive", "Boolean", "true", "Cờ cho phép đăng nhập/hoạt động (@get:PropertyName(\"isActive\") tránh sinh trường rác active)."),
    ("users/{uid}", "User.kt", "avatarUrl", "String", "\"\"", "Ảnh đại diện dùng chung (URL hoặc chuỗi Base64)."),
    ("users/{uid}", "User.kt", "fcmToken", "String", "\"\"", "Token Firebase Cloud Messaging dùng để gửi thông báo đẩy (Push Notification)."),
    ("users/{uid}", "User.kt", "storeId", "String", "\"\"", "Mã chi nhánh quán ăn liên kết (dùng cho MERCHANT, CHEF, DRIVER)."),
    ("users/{uid}", "User.kt", "bossId", "String", "\"\"", "Mã Chủ chuỗi (BOSSMERCHANT) quản lý trực tiếp tài khoản MERCHANT này."),
    ("users/{uid}", "User.kt", "emailVerified", "Boolean", "false", "Trạng thái đã xác thực Email qua mã OTP 6 số (Supabase Auth)."),
    ("users/{uid}", "User.kt", "phoneVerified", "Boolean", "false", "Trạng thái đã xác thực Số điện thoại."),
    ("users/{uid}", "User.kt", "approvalStatus", "String", "\"APPROVED\"", "Trạng thái duyệt hồ sơ đối tác: PENDING, APPROVED, REJECTED."),
    ("users/{uid}", "User.kt", "rejectReason", "String", "\"\"", "Lý do Admin từ chối hồ sơ đối tác (nếu approvalStatus = REJECTED)."),

    # Node customers (Customer.kt)
    ("customers/{customerId}", "Customer.kt", "customerId", "String", "\"\"", "Khóa chính liên kết 1-1 với users.uid."),
    ("customers/{customerId}", "Customer.kt", "avatarUrl", "String", "\"\"", "Ảnh đại diện khách hàng (đọc dự phòng thêm photoUrl, avatar, profileImage)."),
    ("customers/{customerId}", "Customer.kt", "trustScore", "Int", "100", "Điểm uy tín khách hàng (0-100). >=95 Kim Cương (giảm 5k ship), >=90 Vàng (mở COD), <90 Bạc/Hạn chế (cấm COD)."),
    ("customers/{customerId}", "Customer.kt", "totalOrders", "Int", "0", "Tổng số đơn hàng khách đã từng đặt."),
    ("customers/{customerId}", "Customer.kt", "completedOrders", "Int", "0", "Số đơn hàng đã nhận thành công (dùng tính tỷ lệ hoàn thành)."),
    ("customers/{customerId}", "Customer.kt", "boomOrders", "Int", "0", "Số lần khách bom hàng / từ chối nhận đơn COD."),

    # Node customer_addresses (CustomerAddress.kt)
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "addressId", "String", "\"\"", "Mã định danh địa chỉ đã lưu."),
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "customerId", "String", "\"\"", "UID khách hàng sở hữu địa chỉ."),
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "label", "String", "\"Nhà riêng\"", "Nhãn gợi nhớ (Nhà riêng, Công ty, KTX...)."),
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "receiverName", "String", "\"\"", "Tên người nhận hàng tại địa chỉ này."),
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "receiverPhone", "String", "\"\"", "Số điện thoại người nhận hàng."),
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "streetAddress", "String", "\"\"", "Địa chỉ số nhà, tên đường chi tiết."),
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "district", "String", "\"Quận 1\"", "Quận/Huyện tại TP.HCM (tính phí ship nội quận/ngoại quận)."),
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "latitude / longitude", "Double", "0.0", "Tọa độ GPS chính xác đã ghim trên bản đồ."),
    ("customer_addresses/{userId}/{id}", "CustomerAddress.kt", "isDefault", "Boolean", "false", "Đánh dấu địa chỉ mặc định khi vào Checkout."),

    # Node stores (Store.kt)
    ("stores/{storeId}", "Store.kt", "id", "String", "\"\"", "Mã định danh duy nhất của chi nhánh cửa hàng."),
    ("stores/{storeId}", "Store.kt", "bossId", "String", "\"\"", "UID của Chủ chuỗi (BOSSMERCHANT) sở hữu chi nhánh này."),
    ("stores/{storeId}", "Store.kt", "merchantId / merchantName", "String", "\"\"", "UID và tên của Quản lý chi nhánh đang phụ trách vận hành quán."),
    ("stores/{storeId}", "Store.kt", "name / phone / address / district", "String", "\"\"", "Tên quán, sđt liên hệ, địa chỉ chi tiết và quận/huyện."),
    ("stores/{storeId}", "Store.kt", "latitude / longitude (lat / lng)", "Double", "0.0", "Tọa độ GPS của quán ăn dùng để tính khoảng cách giao hàng tới khách và vẽ bản đồ tài xế."),
    ("stores/{storeId}", "Store.kt", "imageUrl / avatarUrl", "String", "\"\"", "Ảnh đại diện / logo quán hiển thị trên trang chủ app Khách."),
    ("stores/{storeId}", "Store.kt", "storeStatus", "String", "\"PENDING\"", "Trạng thái quán: PENDING (chờ Admin duyệt), OPEN (đang mở bán), CLOSED (đóng cửa), LOCKED (bị khóa), REJECTED (bị từ chối)."),
    ("stores/{storeId}", "Store.kt", "isVerified", "Boolean", "false", "Cờ đã qua xác minh hồ sơ KYC bởi Admin hay chưa."),
    ("stores/{storeId}", "Store.kt", "cccdNumber / 4 Url ảnh CCCD", "String", "\"\"", "cccdNumber, cccdFrontUrl, cccdBackUrl, cccdHoldUrl (cầm CCCD), cccdPortraitUrl (chân dung) phục vụ Admin duyệt."),
    ("stores/{storeId}", "Store.kt", "penaltyTier / penaltyReason", "String", "\"NONE\"", "Mức chế tài vi phạm hiện tại (NONE, WARN, HIDE, LOCK) và lý do phạt."),
    ("stores/{storeId}", "Store.kt", "ratingAvg / reviewCount", "Float / Int", "5.0 / 0", "Điểm đánh giá sao trung bình và tổng số lượt đánh giá của quán."),
    ("stores/{storeId}", "Store.kt", "openHours / prepTime / deliveryTime", "String", "\"\"", "Khung giờ mở cửa, thời gian chuẩn bị món và thời gian giao dự kiến."),
    ("stores/{storeId}", "Store.kt", "bankName / bankAccountNumber / bankAccountName / bankBin", "String", "MB Bank / 970422", "Thông tin tài khoản ngân hàng của quán dùng tạo mã QR chuyển khoản VietQR động khi khách đặt món."),
    ("stores/{storeId}", "Store.kt", "momoPhone / momoAccountName", "String", "\"\"", "Thông tin ví MoMo nhận thanh toán của quán."),
    ("stores/{storeId}", "Store.kt", "storeCategories", "List<String>", "[]", "Danh mục thực đơn nội bộ riêng của quán (ví dụ: Món đặc biệt, Đồ uống...)."),
    ("stores/{storeId}", "Store.kt", "dishes", "Map<String, Dish>", "{}", "Danh sách toàn bộ món ăn thuộc quán (mỗi Dish có id, name, basePrice, imageUrl, isAvailable, isHidden, soldCount, ratingAvg, hasOptions, specs, isCombo...)."),

    # Node merchants & bank_accounts & menus & combos & restaurants & chefs & drivers
    ("merchants/{merchantId}", "Merchant.kt", "merchantId / businessLicense / taxCode", "String", "\"\"", "UID chủ quán, số giấy phép kinh doanh và mã số thuế."),
    ("merchants/{merchantId}", "Merchant.kt", "cccdNumber / 4 Url ảnh CCCD", "String", "\"\"", "cccdNumber, cccdFrontUrl, cccdBackUrl, cccdHoldUrl, cccdPortraitUrl đối chiếu KYC."),
    ("merchants/{merchantId}", "Merchant.kt", "bankName / bankAccount", "String", "\"\"", "Tên ngân hàng và số tài khoản đối soát thanh toán."),
    ("merchants/{merchantId}", "Merchant.kt", "approvalStatusId / approvalStatus", "Int / String", "1 / PENDING", "Trạng thái duyệt hồ sơ đối tác: PENDING, APPROVED, REJECTED."),
    ("bank_accounts/{id}", "database_schema.sql", "userId / storeId / bankName / accountNumber", "String", "\"\"", "Tài khoản ngân hàng thụ hưởng liên kết trực tiếp với user hoặc cửa hàng."),
    ("chefs/{chefId}", "Chef.kt", "chefId / storeId / name / phone / email", "String", "\"\"", "Định danh Đầu bếp, mã chi nhánh làm việc và thông tin liên hệ."),
    ("chefs/{chefId}", "Chef.kt", "title / activeOrdersCount / isOnline", "String/Int/Bool", "Đầu Bếp / 0 / true", "Chức danh, số đơn đang chế biến và trạng thái trực tuyến."),
    ("drivers/{driverId}", "Driver.kt", "driverId / storeId / driverCode", "String", "\"\"", "UID tài xế, mã quán trực thuộc và mã nhân viên nội bộ (TX-19...)."),
    ("drivers/{driverId}", "Driver.kt", "cccdNumber / licenseNumber / vehicleType / vehiclePlate", "String", "\"\"", "Thông tin CCCD, GPLX, loại xe (MOTORBIKE/ELECTRIC_BIKE) và biển số xe."),
    ("drivers/{driverId}", "Driver.kt", "dutyStatus", "String", "\"AVAILABLE\"", "Trạng thái ca trực: AVAILABLE (sẵn sàng nhận đơn), DELIVERING (đang giao), OFF_DUTY (nghỉ ca)."),
    ("drivers/{driverId}", "Driver.kt", "currentLat / currentLng", "Double", "0.0", "Tọa độ GPS gần nhất của tài xế."),
    ("drivers/{driverId}", "Driver.kt", "todayOrdersCount / totalOrdersCount / currentOrderCode", "Int / String", "0 / \"\"", "Thống kê số cuốc đã chạy trong ngày, tổng tích lũy và mã đơn đang cầm."),

    # Node orders (Order.kt)
    ("orders/{orderId}", "Order.kt", "orderId", "String", "\"\"", "Mã đơn hàng chuẩn (OISHI_****** hoặc Push Key)."),
    ("orders/{orderId}", "Order.kt", "customerId / customerName / customerPhone", "String", "\"\"", "Định danh, tên và sđt khách đặt đơn."),
    ("orders/{orderId}", "Order.kt", "storeId / shopId / shopName / shopPhone / shopImageUrl", "String", "\"\"", "Định danh và thông tin quán ăn nhận đơn."),
    ("orders/{orderId}", "Order.kt", "chefId / chefName / chefPhone", "String?", "null", "Đầu bếp nhận chế biến đơn (gán khi Chef bấm Nhận nấu)."),
    ("orders/{orderId}", "Order.kt", "driverId / shipperId / shipperName / shipperPhone", "String?", "null", "Tài xế nội bộ được quán giao đơn hoặc tự nhận đơn."),
    ("orders/{orderId}", "Order.kt", "address / deliveryAddress", "String", "\"\"", "Địa chỉ giao hàng cuối cùng tới tay khách."),
    ("orders/{orderId}", "Order.kt", "subtotalAmount / shippingFee / discount / totalPayment", "Long", "0", "Tiền món ăn, phí ship (15k cho 3km đầu + 5k/km), giảm giá và tổng thanh toán."),
    ("orders/{orderId}", "Order.kt", "paymentMethod / paymentMode / paymentStatus / isPaid", "String / Bool", "COD / FULL / UNPAID", "Hình thức thanh toán (COD, BANKING, DEPOSIT, MOMO), chế độ (FULL/DEPOSIT 20%) và trạng thái thanh toán (PAID/UNPAID)."),
    ("orders/{orderId}", "Order.kt", "depositAmount / remainAmount / isDepositPaid", "Long / Bool", "0 / false", "Số tiền đã cọc 20% và số tiền còn lại tài xế cần thu hộ (nếu paymentMode = DEPOSIT)."),
    ("orders/{orderId}", "Order.kt", "status / orderStatus", "String", "\"PENDING\"", "Vòng đời 15 trạng thái: AWAITING_PAYMENT -> PAID -> PENDING -> PREPARING -> READY -> ASSIGNED -> ARRIVED_AT_SHOP -> DELIVERING -> ARRIVED_AT_CUSTOMER -> COMPLETED (hoặc BOOM / RETURNING_TO_STORE / RETURNED_TO_STORE / CANCELLED)."),
    ("orders/{orderId}", "Order.kt", "deliveryToken", "String", "\"\"", "Chuỗi Token QR Code bảo mật để Tài xế quét trên máy Khách xác nhận giao thành công."),
    ("orders/{orderId}", "Order.kt", "items", "Map<String, OrderItem>", "{}", "Chi tiết các món đặt: itemId, dishId, name, price, quantity, selectedOptions (Size/Đường/Đá/Topping), itemSubtotal."),
    ("orders/{orderId}", "Order.kt", "hasIncidentWarning / incidentWarningTime / countdownRound", "Bool / Long / Int", "false / 0 / 1", "Trạng thái Tài xế bật đồng hồ đếm ngược bom hàng (Vòng 1 / Vòng 2) khi khách không nghe máy."),
    ("orders/{orderId}", "Order.kt", "incidentCallProofImage / incidentMessageProofImage", "String", "\"\"", "Ảnh chụp màn hình lịch sử cuộc gọi và tin nhắn do Tài xế tải lên làm bằng chứng báo bom hàng."),
    ("orders/{orderId}", "Order.kt", "cancelRequested / cancelRequestStatus / cancelReason", "Bool / String", "false / \"\"", "Luồng xin hủy đơn khi quán đang nấu (PENDING / APPROVED / REJECTED / WITHDRAWN)."),

    # Node tracking & carts & complaints & reviews & logs & config
    ("tracking/{orderId}", "TrackingLocation.kt", "lat / lng / updatedAt", "Double / Long", "0.0 / 0", "Tọa độ GPS live của tài xế cho đơn hàng (cập nhật chu kỳ 3 giây / 2m)."),
    ("carts/{userId}/{shopId}", "Cart.kt & CartItem.kt", "shopId / shopName / totalAmount / items", "String/Long/Map", "\"\" / 0 / {}", "Giỏ hàng đồng bộ realtime theo từng quán của mỗi user."),
    ("categories/{categoryId}", "Category.kt", "categoryId / categoryName / iconUrl / displayOrder / isActive", "String/Int/Bool", "\"\" / 0 / true", "Danh mục món ăn toàn sàn hiển thị ở trang chủ App Khách."),
    ("dish_reviews/{reviewId}", "DishReview.kt", "reviewId / orderId / dishId / storeId / customerId / rating / comment / imageUrl / likes", "String/Int", "5 / 0", "Đánh giá món ăn kèm ảnh, chữ ký tùy chọn và lượt thích hữu ích."),
    ("complaints/{complaintId}", "Complaint.kt", "complaintId / orderId / senderType / targetType / complaintType / reason / evidenceImage", "String", "PENDING", "Hồ sơ khiếu nại 2 chiều (Khách/Quán/Shipper), phân loại ATTP/WRONG_ITEM/BOM_ORDER/FRAUD..."),
    ("complaints/{complaintId}", "Complaint.kt", "status / penaltyApplied / adminNote / isComplainantPenalized / counterPenaltyApplied", "String / Bool", "PENDING / NONE", "Phán quyết của Admin (RESOLVED/REJECTED), mức phạt (WARN/HIDE/LOCK) và phạt ngược người vu khống."),
    ("trust_score_logs/{logId}", "TrustScoreLog.kt", "logId / customerId / orderId / changeAmount / oldScore / newScore / reason / createdAt", "String/Int/Long", "0", "Nhật ký cộng/trừ điểm uy tín của khách hàng theo từng đơn hàng hoặc phán quyết Admin."),
    ("system_config/delivery_countdown", "DeliveryCountdownConfig.kt", "round1Minutes / round2Minutes / updatedAt", "Int / Long", "15 / 15", "Thời gian đếm ngược sự cố giao hàng vòng 1 và vòng 2 do Admin cài đặt."),
    ("delivery_time_frames/countdown_config", "DeliveryCountdownConfig.kt", "round1Minutes / round2Minutes / updatedAt", "Int / Long", "15 / 15", "Bản sao đồng bộ cấu hình đếm ngược trên nhánh có quyền .read: true cho Tài xế đọc dự phòng.")
]

CODE_MODULES_DETAIL = [
    (":core", "data/firebase/AuthRepository.kt", "Quản lý toàn bộ luồng Đăng nhập, Đăng ký, Đồng bộ Role, Xác thực OTP Gmail, Ngoại lệ admin@gmail.com, Khởi tạo Auth phụ cho Boss/Quán tạo nhân sự."),
    (":core", "data/firebase/FirebaseRepository.kt", "Kho dữ liệu trung tâm (~3.858 dòng): đọc/ghi realtime (Flow/callbackFlow) cho tất cả 23 Node Firebase, cơ chế readCacheFirst, tính điểm uy tín, xử lý khiếu nại 3 mức, quản lý Boss/Chef/Driver."),
    (":core", "data/firebase/EmailOtpApi.kt", "Kết nối Supabase Auth REST API để gửi & xác thực mã OTP 6 số qua Gmail (kèm bộ nhớ đệm verifiedOtpCache 10 phút)."),
    (":core", "util/AccountLockWatcher.kt", "Lắng nghe node users/{uid} qua WebSocket Firebase: hiển thị ngay Dialog khóa tài khoản không thể tắt (<500ms) nếu Admin khóa tài khoản đang online."),
    (":core", "util/CccdOcrHelper.kt", "Dùng Google ML Kit Text Recognition trích xuất tự động số CCCD, họ tên, ngày sinh, giới tính, địa chỉ từ ảnh chụp mặt trước CCCD."),
    (":core", "util/MomoPaymentGatewayClient.kt & BankData.kt", "Tích hợp API api.vietqr.io (với danh sách 54 ngân hàng chuẩn NAPAS 24/7) để sinh mã VietQR / MoMo thanh toán động theo mã đơn OISHI_******."),
    (":core", "util/QRCodeUtils.kt & AppConstants.kt", "Sinh mã QR deliveryToken xác thực giao hàng (ZXing), chuẩn hóa mã đơn hàng, tập trung các hằng số FirebaseNodes, ShippingRules, TrustScoreRules."),

    (":app-customer", "ui/auth/* (Login, Register, OtpVerificationActivity)", "Đăng nhập, đăng ký tài khoản Khách hàng kèm xác thực mã OTP gửi về Gmail."),
    (":app-customer", "ui/home/* (HomeActivity, HomeViewModel, Adapters)", "Trang chủ hiển thị danh mục món ăn, quán gần đây, Top bán chạy/yêu thích (lọc các quán OPEN và không bị penaltyTier=HIDE/LOCK)."),
    (":app-customer", "ui/menu/* (MenuActivity, DishOptionBottomSheet, DishReviewsBottomSheet)", "Xem thực đơn quán, chọn tùy chọn Size/Đường/Đá/Topping, xem & lọc đánh giá món ăn."),
    (":app-customer", "ui/cart/* & ui/checkout/* & ui/payment/*", "Quản lý giỏ hàng theo quán (Debounce sync), chọn/ghim địa chỉ bản đồ (SelectLocationActivity), kiểm tra điều kiện COD (trustScore >= 90), thanh toán VietQR/MoMo/Cọc 20%."),
    (":app-customer", "ui/tracking/* & ui/order/* & ui/complaint/*", "Theo dõi trạng thái đơn realtime, bản đồ GPS vị trí xe tài xế live từ tracking/{orderId}, chat trực tiếp với quán, hiện QR deliveryToken, đánh giá món & gửi khiếu nại."),

    (":app-internal", "ui/auth/* & StoreVerificationActivity", "Đăng nhập chung 4 vai trò nội bộ (Boss/Quản lý/Bếp/Tài xế), đăng ký & xác minh đối tác kèm chụp/tải ảnh CCCD mặt trước + mặt sau, chọn tọa độ quán trên bản đồ Leaflet (select_map.html)."),
    (":app-internal", "ui/boss/* (BossDashboardActivity)", "Bảng điều khiển Chủ chuỗi: Quản lý danh sách chi nhánh quán, tạo chi nhánh mới, tạo/gán/khóa tài khoản Quản lý chi nhánh (MERCHANT), xem tổng quan."),
    (":app-internal", "ui/shop/* (OrderListFragment, MenuManageFragment, StoreProfileFragment, StoreReportFragment)", "Bảng điều khiển Quản lý quán: Kanban tiếp nhận & duyệt đơn, công tắc Mở/Đóng bán (switchShopStatus), quản lý món & danh mục quán, chat khách, báo cáo doanh thu, điều phối Bếp/Shipper."),
    (":app-internal", "ui/chef/* (ChefFragment, ChefViewModel)", "Giao diện Đầu bếp nội bộ: Nhận đơn mới (PENDING -> PREPARING), đánh dấu nấu xong (READY) để tài xế giao."),
    (":app-internal", "ui/driver/* & ui/ship/* (ShipperFragment, DeliveryTripActivity, ShipperIncomeFragment)", "Giao diện Tài xế nội bộ: Bật/tắt ca trực, nhận đơn gộp, lộ trình 4 bước GPS liên tục trên bản đồ (map_step1..4.html), quét QR hoàn tất, đồng hồ đếm ngược bom hàng & tải ảnh chứng cứ."),
    (":app-internal", "service/LocationForegroundService.kt", "Foreground Service chạy nền cập nhật GPS thực tế theo danh sách orderId thật lên tracking/{orderId} & drivers/{driverId}."),
    (":app-internal", "service/BankNotificationListenerService.kt & MerchantKeepAliveService.kt", "Lắng nghe thông báo biến động số dư ngân hàng 24/7 trên máy Quán/Boss: tự động khớp mã đơn OISHI_****** và số tiền để chuyển đơn sang paymentStatus=PAID."),

    (":app-admin", "ui/auth/* & ui/main/AdminMainActivity", "Đăng nhập Admin (miễn OTP cho admin@gmail.com) và khung điều hướng 5 phân hệ quản trị."),
    (":app-admin", "ui/dashboard/AdminDashboardFragment", "Thống kê tổng quan toàn sàn (tổng đơn, doanh thu, cửa hàng chờ duyệt, khiếu nại) & cài đặt thời gian đếm ngược sự cố round1/round2."),
    (":app-admin", "ui/store/* (AdminStoreListFragment, DetailBottomSheet)", "Kiểm duyệt hồ sơ KYC CCCD 4 ảnh của quán mới, Duyệt/Từ chối có lý do, khóa/mở quán, sửa thông tin chi tiết."),
    (":app-admin", "ui/category/* (AdminCategoryListFragment)", "Quản lý danh mục món ăn toàn sàn: Thêm/sửa/xóa, bật/tắt hiển thị, tự động chen và đẩy lại số thứ tự displayOrder."),
    (":app-admin", "ui/complaint/* (AdminComplaintListFragment, Detail, ResolveDialog)", "Xem ảnh bằng chứng khiếu nại và đưa ra phán quyết 3 mức chế tài (WARN/HIDE/LOCK) hoặc phạt ngược người báo cáo sai."),
    (":app-admin", "ui/monitor/* (AdminMonitorFragment)", "Giám sát realtime toàn bộ tài khoản Users & Đơn hàng, chỉnh sửa điểm uy tín khách hàng có ghi log, khóa/mở khóa tài khoản tức thì.")
]

CORE_FLOWS = [
    ("1. Xác thực OTP Gmail & Bảo vệ tài khoản Realtime",
     "EmailOtpApi.kt, AuthRepository.kt, AccountLockWatcher.kt",
     "1) Khi Khách hàng / Đối tác đăng ký hoặc đăng nhập cần xác thực, app gọi Supabase Auth REST API gửi OTP 6 số về Gmail.\n"
     "2) Sau khi xác thực OTP thành công (lưu cache 10 phút), app tạo/đăng nhập Firebase Auth và đồng bộ bản ghi lên node users/{uid}.\n"
     "3) Tài khoản admin@gmail.com được miễn OTP và tự động đồng bộ role=ADMIN.\n"
     "4) Khi người dùng đang mở app, AccountLockWatcher gắn listener WebSocket tại users/{uid}: nếu Admin bấm Khóa (status=LOCKED / isActive=false), app bật cảnh báo không thể tắt trong <500ms và buộc đăng xuất."),
    ("2. Quy trình Chủ chuỗi (BOSS) & Kiểm duyệt KYC Quán (CCCD)",
     "BossDashboardActivity.kt, StoreVerificationActivity.kt, AdminStoreListFragment.kt",
     "1) Chủ quán/Boss gửi hồ sơ xác minh: nhập thông tin quán, chọn vị trí GPS trên bản đồ, chụp/tải lên ảnh CCCD mặt trước (cccdFrontUrl) và mặt sau (cccdBackUrl), nhập STK ngân hàng đối soát.\n"
     "2) Hồ sơ lưu vào stores/{storeId} với storeStatus=PENDING và merchants/{uid} (hỗ trợ lưu đủ tới 4 loại ảnh CCCD trong model).\n"
     "3) Admin mở :app-admin hoặc admin.html đối chiếu ảnh CCCD -> Duyệt (storeStatus=OPEN, isVerified=true) hoặc Từ chối kèm lý do.\n"
     "4) BOSSMERCHANT có thể tạo nhiều chi nhánh quán (lưu trong boss_stores/{bossUid}), tạo tài khoản MERCHANT bằng Firebase Auth phụ (không văng phiên Boss) và gán quản lý vào từng chi nhánh."),
    ("3. Quy trình Đặt hàng, Kiểm tra Điểm Uy Tín & Thanh toán Tự động",
     "CheckoutViewModel.kt, MomoPaymentGatewayClient.kt, BankNotificationListenerService.kt",
     "1) Hệ thống đọc customers/{uid}/trustScore: nếu >=90 điểm mới được chọn COD; >=95 điểm được giảm 5.000đ phí ship; <90 điểm buộc chọn Cọc 20% hoặc Chuyển khoản 100%.\n"
     "2) Phí ship tự tính theo khoảng cách GPS: 15.000đ cho 3km đầu + 5.000đ/km tiếp theo.\n"
     "3) Nếu chọn VietQR/MoMo, MomoPaymentGatewayClient gọi api.vietqr.io tạo QR kèm cú pháp OISHI_******.\n"
     "4) Trên máy Quản lý quán/Boss, dịch vụ BankNotificationListenerService chạy ngầm đọc thông báo biến động số dư ngân hàng -> khớp mã OISHI_****** & số tiền -> tự động cập nhật paymentStatus=PAID, status=PAID lên Firebase trong vài giây."),
    ("4. Quy trình Vận hành Nội bộ Quán (Quản lý -> Đầu bếp -> Tài xế)",
     "OrderListFragment.kt, ChefFragment.kt, ShipperFragment.kt, FirebaseRepository.kt",
     "1) Đơn hợp lệ (PAID hoặc COD PENDING) hiện lên bảng Kanban của Quản lý quán & hàng đợi Đầu bếp.\n"
     "2) Đầu bếp (CHEF) bấm Nhận nấu -> gán chefId, chuyển trạng thái sang PREPARING; nấu xong bấm Hoàn tất món -> chuyển READY.\n"
     "3) Quản lý quán hoặc Tài xế nội bộ (DRIVER, mã TX-xx) nhận đơn (ASSIGNED) -> danh sách đơn gộp được đẩy sang màn hình chuyến đi."),
    ("5. Quy trình Giao hàng GPS 4 Bước & Xác thực QR Token",
     "DeliveryTripActivity.kt, LocationForegroundService.kt, TrackingViewModel.kt, QRCodeUtils.kt",
     "1) Khi tài xế nhận chuyến đi, LocationForegroundService chạy nền và DeliveryTripActivity bật lắng nghe GPS liên tục (chu kỳ 3 giây / 2 mét).\n"
     "2) Tọa độ xe thực tế được đẩy lên đồng thời tracking/{orderId} (cho từng đơn trong chuyến gộp) và drivers/{driverId}, đồng thời đẩy vào WebView map_step1..4.html để icon xe lăn bánh mượt mà.\n"
     "3) Tài xế đi qua 4 bước: Đến quán (ARRIVED_AT_SHOP) -> Lấy hàng & Đang giao (DELIVERING) -> Đến nơi (ARRIVED_AT_CUSTOMER) -> Quét QR deliveryToken trên máy Khách (hoặc đối chiếu mã) để hoàn tất (COMPLETED), cộng +2 điểm uy tín cho khách."),
    ("6. Quy trình Đếm ngược Sự cố Bom hàng 2 Vòng & Khiếu nại 3 Chế tài",
     "DeliveryTripActivity.kt, DeliveryCountdownConfig.kt, AdminComplaintListFragment.kt, FirebaseRepository.kt",
     "1) Nếu khách không nghe máy khi giao, tài xế bật đếm ngược sự cố đọc thời gian từ delivery_time_frames/countdown_config (hoặc system_config/delivery_countdown) gồm round1Minutes và round2Minutes.\n"
     "2) Tài xế tải lên ảnh chụp bằng chứng cuộc gọi/nhắn tin (incidentCallProofImage, incidentMessageProofImage) -> hết thời gian quy định có thể xác nhận BOOM.\n"
     "3) Hệ thống tự động trừ -30 điểm uy tín của khách (ghi log vào trust_score_logs), tăng boomOrders.\n"
     "4) Khách hàng/Quán/Tài xế có thể gửi khiếu nại kèm ảnh vào complaints/{id}. Admin phán quyết 1 trong 3 mức chế tài: WARN (Cảnh cáo), HIDE (Ẩn quán 24h), LOCK (Khóa quán/tài khoản), hoặc phạt ngược nếu khiếu nại sai sự thật.")
]

# ==============================================================================
# HÀM HỖ TRỢ SINH FILE EXCEL (.xlsx)
# ==============================================================================

def estimate_row_height(row_values, col_widths, min_height=22, line_height=15.5):
    max_lines = 1
    for val, width in zip(row_values, col_widths):
        text = str(val or "")
        effective_w = max(width - 3, 10)
        lines = 0
        for paragraph in text.split("\n"):
            lines += max(1, math.ceil(len(paragraph) / effective_w))
        if lines > max_lines:
            max_lines = lines
    return max(min_height, max_lines * line_height + 8)

def style_banner(ws, max_col, title_text, subtitle_text):
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=max_col)
    ws.merge_cells(start_row=2, start_column=1, end_row=2, end_column=max_col)
    c1 = ws.cell(row=1, column=1, value=title_text)
    c1.font = Font(name="Segoe UI", size=14, bold=True, color="FFFFFF")
    c1.fill = PatternFill("solid", fgColor="1E293B")
    c1.alignment = Alignment(horizontal="left", vertical="center", indent=1)
    ws.row_dimensions[1].height = 32

    c2 = ws.cell(row=2, column=1, value=subtitle_text)
    c2.font = Font(name="Segoe UI", size=10, italic=True, color="9A3412")
    c2.fill = PatternFill("solid", fgColor="FFEDD5")
    c2.alignment = Alignment(horizontal="left", vertical="center", indent=1)
    ws.row_dimensions[2].height = 22

def write_styled_table(ws, start_row, headers, rows, col_widths, header_color="EA580C"):
    thin = Side(border_style="thin", color="CBD5E1")
    border = Border(left=thin, right=thin, top=thin, bottom=thin)

    # Headers
    for col_idx, h in enumerate(headers, 1):
        cell = ws.cell(row=start_row, column=col_idx, value=h)
        cell.font = Font(name="Segoe UI", size=10.5, bold=True, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor=header_color)
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        cell.border = border
    ws.row_dimensions[start_row].height = 28

    # Rows
    fill_even = PatternFill("solid", fgColor="F8FAFC")
    fill_odd = PatternFill("solid", fgColor="FFFFFF")
    for r_offset, row_data in enumerate(rows, 1):
        r_idx = start_row + r_offset
        fill = fill_even if r_offset % 2 == 0 else fill_odd
        for col_idx, val in enumerate(row_data, 1):
            cell = ws.cell(row=r_idx, column=col_idx, value=val)
            is_bold_col = (col_idx <= 2)
            cell.font = Font(name="Segoe UI", size=10, bold=is_bold_col, color="0F172A")
            cell.fill = fill
            h_align = "center" if (col_idx == 1 and str(val).isdigit()) else "left"
            cell.alignment = Alignment(horizontal=h_align, vertical="top", wrap_text=True)
            cell.border = border
        ws.row_dimensions[r_idx].height = estimate_row_height(row_data, col_widths)

    for col_idx, w in enumerate(col_widths, 1):
        ws.column_dimensions[get_column_letter(col_idx)].width = w

    return start_row + len(rows) + 1

def generate_excel():
    wb = Workbook()

    # Sheet 1: 1_Tong_Quan_Kien_Truc
    ws1 = wb.active
    ws1.title = "1_Tong_Quan_Kien_Truc"
    ws1.views.sheetView[0].showGridLines = True
    style_banner(ws1, 4,
                 "OISHI FOOD — TỔNG QUAN KIẾN TRÚC DỰ ÁN & PHÂN QUYỀN RBAC",
                 "Tổng hợp từ mã nguồn dự án Android Multi-module & Firebase Realtime Database")

    ws1.merge_cells("A4:D4")
    sec1 = ws1.cell(row=4, column=1, value="I. THÔNG TIN KIẾN TRÚC & CÔNG NGHỆ CỐT LÕI")
    sec1.font = Font(name="Segoe UI", size=11, bold=True, color="1E293B")
    ws1.row_dimensions[4].height = 24
    # Pad cột B..D để gộp ô giữ nguyên khung viền và chiều cao dòng chuẩn
    arch_rows_4col = [(k, v, "", "") for k, v in ARCHITECTURE_OVERVIEW]
    next_r = write_styled_table(
        ws1, 5,
        ["Hạng mục", "Chi tiết triển khai trong dự án", "", ""],
        arch_rows_4col,
        [24, 36, 32, 74],
        header_color="1E293B"
    )
    for r in range(5, next_r):
        ws1.merge_cells(start_row=r, start_column=2, end_row=r, end_column=4)
        if r > 5:
            val_pair = ARCHITECTURE_OVERVIEW[r - 6]
            ws1.row_dimensions[r].height = estimate_row_height(val_pair, [24, 142])

    next_r += 1
    ws1.merge_cells(start_row=next_r, start_column=1, end_row=next_r, end_column=4)
    sec2 = ws1.cell(row=next_r, column=1, value="II. CẤU TRÚC 4 MODULES GRADLE TRONG DỰ ÁN")
    sec2.font = Font(name="Segoe UI", size=11, bold=True, color="1E293B")
    ws1.row_dimensions[next_r].height = 24
    next_r = write_styled_table(
        ws1, next_r + 1,
        ["Module", "Package / Namespace", "Vai trò", "Thành phần chính & Chức năng"],
        MODULES_SUMMARY,
        [24, 36, 32, 74],
        header_color="EA580C"
    )

    next_r += 1
    ws1.merge_cells(start_row=next_r, start_column=1, end_row=next_r, end_column=4)
    sec3 = ws1.cell(row=next_r, column=1, value="III. HỆ THỐNG PHÂN QUYỀN VAI TRÒ (RBAC)")
    sec3.font = Font(name="Segoe UI", size=11, bold=True, color="1E293B")
    ws1.row_dimensions[next_r].height = 24
    roles_4col = [(r[0], f"roleId: {r[1]} | {r[2]}", r[3], r[4]) for r in ROLES_OVERVIEW]
    write_styled_table(
        ws1, next_r + 1,
        ["Mã Role", "Định danh & Ứng dụng", "Tên vai trò", "Quyền hạn & Nghiệp vụ chi tiết"],
        roles_4col,
        [24, 36, 32, 74],
        header_color="0F766E"
    )

    # Sheet 2: 2_Danh_Sach_23_Nodes_Firebase
    ws2 = wb.create_sheet("2_Danh_Sach_23_Nodes_Firebase")
    style_banner(ws2, 9,
                 "GIẢI THÍCH CHI TIẾT TOÀN BỘ 23 NHÓM NODE TRÊN FIREBASE REALTIME DATABASE",
                 "Đối chiếu trực tiếp từ database.rules.json, AppConstants.kt, FirebaseRepository.kt và core/data/model/*.kt")
    write_styled_table(
        ws2, 4,
        ["STT", "Tên Node Firebase", "Đường dẫn JSON", "Model Kotlin ánh xạ",
         "Nhóm Chức Năng", "Tác dụng chi tiết", "Quyền Đọc (.read)", "Quyền Ghi (.write)", "Chỉ mục (.indexOn)"],
        FIREBASE_NODES,
        [6, 26, 32, 22, 22, 60, 22, 28, 30],
        header_color="EA580C"
    )
    ws2.freeze_panes = "C5"

    # Sheet 3: 3_Chi_Tiet_Truong_Du_Lieu
    ws3 = wb.create_sheet("3_Chi_Tiet_Truong_Du_Lieu")
    style_banner(ws3, 7,
                 "TỪ ĐIỂN THUỘC TÍNH (FIELDS) CHI TIẾT CỦA TỪNG NODE TRÊN FIREBASE",
                 "Giải thích tên biến, kiểu dữ liệu, giá trị mặc định và tác dụng nghiệp vụ trong mã nguồn Kotlin")
    fields_with_stt = [(i + 1,) + row for i, row in enumerate(NODE_FIELDS_DETAIL)]
    write_styled_table(
        ws3, 4,
        ["STT", "Node / Đường dẫn", "File Model Kotlin", "Tên trường (Field)", "Kiểu dữ liệu", "Mặc định", "Tác dụng & Ý nghĩa chi tiết"],
        fields_with_stt,
        [6, 28, 22, 34, 16, 18, 65],
        header_color="1D4ED8"
    )
    ws3.freeze_panes = "D5"

    # Sheet 4: 4_Chi_Tiet_4_Modules_Code
    ws4 = wb.create_sheet("4_Chi_Tiet_4_Modules_Code")
    style_banner(ws4, 4,
                 "CHI TIẾT CẤU TRÚC MÃ NGUỒN 4 MODULES GRADLE",
                 "Phân tích vai trò từng lớp Activity, Fragment, ViewModel, Repository và Foreground Service")
    mods_with_stt = [(i + 1,) + row for i, row in enumerate(CODE_MODULES_DETAIL)]
    write_styled_table(
        ws4, 4,
        ["STT", "Module", "File / Package tiêu biểu", "Chức năng & Trách nhiệm chi tiết"],
        mods_with_stt,
        [6, 16, 42, 85],
        header_color="7C3AED"
    )
    ws4.freeze_panes = "C5"

    # Sheet 5: 5_Luong_Nghiep_Vu_Cot_Loi
    ws5 = wb.create_sheet("5_Luong_Nghiep_Vu_Cot_Loi")
    style_banner(ws5, 4,
                 "6 LUỒNG NGHIỆP VỤ CỐT LÕI & CƠ CHẾ ĐẶC THÙ CỦA HỆ THỐNG OISHI FOOD",
                 "Mô tả cách các ứng dụng Khách hàng, Nội bộ, Admin và Firebase phối hợp theo thời gian thực")
    flows_with_stt = [(i + 1,) + row for i, row in enumerate(CORE_FLOWS)]
    write_styled_table(
        ws5, 4,
        ["STT", "Tên Luồng Nghiệp Vụ", "Thành phần tham gia chính", "Quy trình hoạt động chi tiết từng bước"],
        flows_with_stt,
        [6, 34, 38, 86],
        header_color="0F766E"
    )
    ws5.freeze_panes = "C5"

    wb.save(XLSX_PATH)
    print(f"✓ Đã tạo file Excel: {XLSX_PATH}")


# ==============================================================================
# HÀM HỖ TRỢ SINH FILE WORD (.docx)
# ==============================================================================

def set_cell_shading(cell, fill_hex):
    shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    cell._tc.get_or_add_tcPr().append(shading)

def set_table_borders(table, border_color="CBD5E1"):
    tblPr = table._tbl.tblPr
    borders = parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        f'<w:top w:val="single" w:sz="4" w:space="0" w:color="{border_color}"/>'
        f'<w:left w:val="single" w:sz="4" w:space="0" w:color="{border_color}"/>'
        f'<w:bottom w:val="single" w:sz="4" w:space="0" w:color="{border_color}"/>'
        f'<w:right w:val="single" w:sz="4" w:space="0" w:color="{border_color}"/>'
        f'<w:insideH w:val="single" w:sz="4" w:space="0" w:color="{border_color}"/>'
        f'<w:insideV w:val="single" w:sz="4" w:space="0" w:color="{border_color}"/>'
        f'</w:tblBorders>'
    )
    tblPr.append(borders)

def add_doc_table(doc, headers, rows, col_widths_inches=None, header_hex="1E293B"):
    table = doc.add_table(rows=1 + len(rows), cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(table)

    # Header row
    hdr_cells = table.rows[0].cells
    trPr = table.rows[0]._tr.get_or_add_trPr()
    trPr.append(parse_xml(f'<w:tblHeader {nsdecls("w")}/>'))
    for idx, h in enumerate(headers):
        cell = hdr_cells[idx]
        set_cell_shading(cell, header_hex)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(3)
        p.paragraph_format.space_after = Pt(3)
        run = p.add_run(str(h))
        run.bold = True
        run.font.name = "Calibri"
        run.font.size = Pt(9.5)
        run.font.color.rgb = RGBColor(255, 255, 255)
        if col_widths_inches and idx < len(col_widths_inches):
            cell.width = Inches(col_widths_inches[idx])

    # Data rows
    for r_i, row_data in enumerate(rows):
        row_cells = table.rows[r_i + 1].cells
        bg = "F8FAFC" if r_i % 2 == 1 else "FFFFFF"
        for c_i, val in enumerate(row_data):
            cell = row_cells[c_i]
            set_cell_shading(cell, bg)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.TOP
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(2.5)
            p.paragraph_format.space_after = Pt(2.5)
            run = p.add_run(str(val))
            run.font.name = "Calibri"
            run.font.size = Pt(9)
            if c_i in (0, 1):
                run.bold = True
            run.font.color.rgb = RGBColor(15, 23, 42)
            if col_widths_inches and c_i < len(col_widths_inches):
                cell.width = Inches(col_widths_inches[c_i])
    doc.add_paragraph().paragraph_format.space_after = Pt(4)

def add_heading_styled(doc, text, level=1):
    h = doc.add_heading(text, level=level)
    for r in h.runs:
        r.font.name = "Calibri"
        if level == 1:
            r.font.size = Pt(15)
            r.font.color.rgb = RGBColor(234, 88, 12)
        elif level == 2:
            r.font.size = Pt(12.5)
            r.font.color.rgb = RGBColor(30, 41, 59)
        else:
            r.font.size = Pt(11)
            r.font.color.rgb = RGBColor(15, 118, 110)
    return h

def generate_word():
    doc = Document()
    # Set narrow margins (0.6 inch) so tables fit comfortably
    for section in doc.sections:
        section.top_margin = Inches(0.6)
        section.bottom_margin = Inches(0.6)
        section.left_margin = Inches(0.6)
        section.right_margin = Inches(0.6)

    normal_style = doc.styles["Normal"]
    normal_style.font.name = "Calibri"
    normal_style.font.size = Pt(10.5)

    # Title Banner
    title_p = doc.add_paragraph()
    title_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title_run = title_p.add_run("TÀI LIỆU GIẢI THÍCH CHI TIẾT TOÀN BỘ DỰ ÁN\nVÀ CẤU TRÚC CÁC NODE FIREBASE REALTIME DATABASE")
    title_run.bold = True
    title_run.font.name = "Calibri"
    title_run.font.size = Pt(18)
    title_run.font.color.rgb = RGBColor(30, 41, 59)

    sub_p = doc.add_paragraph()
    sub_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    sub_run = sub_p.add_run("Hệ thống Giao đồ ăn trực tuyến Oishi Food (Android Multi-Module + Firebase Realtime DB + Admin Portal)")
    sub_run.italic = True
    sub_run.font.name = "Calibri"
    sub_run.font.size = Pt(11)
    sub_run.font.color.rgb = RGBColor(234, 88, 12)

    doc.add_paragraph()

    # PHẦN 1
    add_heading_styled(doc, "PHẦN 1: TỔNG QUAN KIẾN TRÚC HỆ THỐNG & PHÂN QUYỀN RBAC", level=1)
    p = doc.add_paragraph(
        "Dự án Oishi Food được thiết kế theo mô hình Multi-module Android Clean MVVM kết hợp Firebase Realtime Database, "
        "Firebase Authentication, Firebase Cloud Messaging, Supabase Auth OTP và Google ML Kit OCR. Toàn hệ thống phục vụ đồng thời "
        "nhiều nhóm người dùng khác nhau trên 3 ứng dụng Android độc lập và 1 cổng quản trị Web (admin.html)."
    )

    add_heading_styled(doc, "1.1. Thông số kỹ thuật & Công nghệ sử dụng", level=2)
    add_doc_table(doc, ["Hạng mục", "Chi tiết triển khai thực tế"], ARCHITECTURE_OVERVIEW, [2.1, 5.1], "1E293B")

    add_heading_styled(doc, "1.2. Kiến trúc 4 Modules Gradle", level=2)
    add_doc_table(doc, ["Module", "Namespace", "Vai trò", "Nội dung chính"], MODULES_SUMMARY, [1.1, 1.8, 1.5, 2.8], "EA580C")

    add_heading_styled(doc, "1.3. Hệ thống Phân quyền RBAC (Actors & Roles)", level=2)
    roles_table = [(r[0], f"{r[1]} ({r[2]})", r[3], r[4]) for r in ROLES_OVERVIEW]
    add_doc_table(doc, ["Mã Role", "ID & App sử dụng", "Tên vai trò", "Quyền hạn & Nghiệp vụ chi tiết"], roles_table, [1.2, 1.5, 1.3, 3.2], "0F766E")

    # PHẦN 2
    add_heading_styled(doc, "PHẦN 2: GIẢI THÍCH CHI TIẾT TOÀN BỘ CÁC NODE TRÊN FIREBASE", level=1)
    doc.add_paragraph(
        "Dựa trên tệp phân quyền bảo mật database.rules.json, AppConstants.kt và lớp truy vấn trung tâm FirebaseRepository.kt, "
        "cơ sở dữ liệu Realtime Database của dự án được tổ chức thành 23 nhóm Node gốc sau đây:"
    )

    nodes_table = [(n[0], f"{n[1]}\n({n[2]})", f"{n[3]}\n[{n[4]}]", n[5], f"Đọc: {n[6]}\nGhi: {n[7]}\nIndex: {n[8]}") for n in FIREBASE_NODES]
    add_doc_table(
        doc,
        ["STT", "Node & Đường dẫn", "Model & Nhóm", "Tác dụng chi tiết trên hệ thống", "Bảo mật & Index"],
        nodes_table,
        [0.45, 1.65, 1.25, 2.45, 1.4],
        "EA580C"
    )

    # PHẦN 3
    add_heading_styled(doc, "PHẦN 3: TỪ ĐIỂN THUỘC TÍNH (FIELDS) CHI TIẾT CỦA TỪNG NODE", level=1)
    doc.add_paragraph(
        "Dưới đây là giải thích chi tiết từng trường dữ liệu (field) được định nghĩa trong các Data Class Kotlin (core/data/model/*.kt) "
        "và ghi trực tiếp lên Firebase Realtime Database:"
    )

    # Group fields by Node name
    grouped_fields = {}
    for row in NODE_FIELDS_DETAIL:
        node_key = f"{row[0]}  —  ({row[1]})"
        grouped_fields.setdefault(node_key, []).append(row[2:])

    for node_key, fields in grouped_fields.items():
        add_heading_styled(doc, f"Node: {node_key}", level=2)
        add_doc_table(
            doc,
            ["Tên trường (Field)", "Kiểu dữ liệu", "Mặc định", "Tác dụng & Ý nghĩa nghiệp vụ"],
            fields,
            [1.8, 1.0, 1.0, 3.4],
            "1D4ED8"
        )

    # PHẦN 4
    add_heading_styled(doc, "PHẦN 4: CẤU TRÚC MÃ NGUỒN CHI TIẾT THEO TỪNG MODULE", level=1)
    mods_table = [(m[0], m[1], m[2]) for m in CODE_MODULES_DETAIL]
    add_doc_table(
        doc,
        ["Module", "Lớp / Thành phần tiêu biểu", "Chức năng & Trách nhiệm chi tiết"],
        mods_table,
        [1.1, 2.3, 3.8],
        "7C3AED"
    )

    # PHẦN 5
    add_heading_styled(doc, "PHẦN 5: GIẢI THÍCH 6 LUỒNG NGHIỆP VỤ CỐT LÕI & CƠ CHẾ ĐẶC THÙ", level=1)
    for title, actors_files, desc in CORE_FLOWS:
        add_heading_styled(doc, title, level=2)
        p_meta = doc.add_paragraph()
        r_lbl = p_meta.add_run("Thành phần tham gia: ")
        r_lbl.bold = True
        r_val = p_meta.add_run(actors_files)
        r_val.italic = True
        r_val.font.color.rgb = RGBColor(15, 118, 110)

        for step_line in desc.split("\n"):
            p_step = doc.add_paragraph(step_line)
            p_step.paragraph_format.left_indent = Inches(0.2)
            p_step.paragraph_format.space_after = Pt(3)

    doc.save(DOCX_PATH)
    print(f"✓ Đã tạo file Word:  {DOCX_PATH}")

if __name__ == "__main__":
    generate_excel()
    generate_word()
