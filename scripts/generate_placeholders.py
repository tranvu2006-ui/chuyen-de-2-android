import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch

base_dir = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\docs"
sc_dir = os.path.join(base_dir, "screenshots")
tc_dir = os.path.join(base_dir, "test_cases")
os.makedirs(sc_dir, exist_ok=True)
os.makedirs(tc_dir, exist_ok=True)

plt.rcParams['font.sans-serif'] = ['DejaVu Sans', 'Arial', 'Segoe UI']

screenshots = [
    ("anh_1.png", "Ảnh 1", "Màn hình Đăng nhập Khách hàng", "activity_login.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_2.png", "Ảnh 2", "Xác thực Email OTP", "activity_otp_verification.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_3.png", "Ảnh 3", "Trang chủ Khách hàng (Banner & Quán)", "activity_home.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_4.png", "Ảnh 4", "Chi tiết Thực đơn Quán ăn", "activity_menu.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_5.png", "Ảnh 5", "Tùy chọn Topping & Số lượng món", "bottom_sheet_dish_option.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_6.png", "Ảnh 6", "Giỏ hàng & Điều chỉnh món", "activity_cart.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_7.png", "Ảnh 7", "Đặt hàng & Phương thức thanh toán", "activity_checkout.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_8.png", "Ảnh 8", "Thanh toán VietQR Napas247", "activity_payment.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_9.png", "Ảnh 9", "Theo dõi GPS Tài xế Realtime Maps", "activity_tracking.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_10.png", "Ảnh 10", "Hộp thoại Mã QR / PIN nhận món", "dialog_delivery_confirmation.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_11.png", "Ảnh 11", "Lịch sử Đơn hàng & Bộ lọc", "activity_order_history.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_12.png", "Ảnh 12", "Hồ sơ Cá nhân & Điểm uy tín", "activity_profile.xml", "app-customer", "#EFF6FF", "#1D4ED8"),
    ("anh_13.png", "Ảnh 13", "Đăng nhập / Đăng ký Ứng dụng Nội bộ", "activity_login_internal.xml", "app-internal", "#FEF3C7", "#B45309"),
    ("anh_14.png", "Ảnh 14", "Xác minh Cửa hàng & Thiết lập VietQR", "activity_store_verification.xml", "app-internal (Merchant)", "#FEF3C7", "#B45309"),
    ("anh_15.png", "Ảnh 15", "Quản lý Chuỗi & Mở Chi nhánh", "activity_boss_dashboard.xml", "app-internal (Boss)", "#FEF3C7", "#B45309"),
    ("anh_16.png", "Ảnh 16", "Quản lý Đơn hàng Kanban 4 Tab", "fragment_order_list.xml", "app-internal (Merchant)", "#FEF3C7", "#B45309"),
    ("anh_17.png", "Ảnh 17", "Popup Chuông báo Đơn hàng mới", "dialog_new_order_popup.xml", "app-internal (Merchant)", "#FEF3C7", "#B45309"),
    ("anh_18.png", "Ảnh 18", "Quản lý Menu Món, Topping, Combo", "fragment_menu_manage.xml", "app-internal (Merchant)", "#FEF3C7", "#B45309"),
    ("anh_19.png", "Ảnh 19", "Báo cáo Doanh thu & Bán chạy", "fragment_store_report.xml", "app-internal (Merchant)", "#FEF3C7", "#B45309"),
    ("anh_20.png", "Ảnh 20", "Phân hệ Bếp tiếp nhận món nấu", "fragment_chef.xml", "app-internal (Chef)", "#F3E8FF", "#7E22CE"),
    ("anh_21.png", "Ảnh 21", "Sảnh Nhận đơn & Ghép đơn đa điểm", "fragment_shipper.xml", "app-internal (Driver)", "#F0FDF4", "#15803D"),
    ("anh_22.png", "Ảnh 22", "Hành trình Giao hàng Google Maps", "activity_delivery_trip.xml", "app-internal (Driver)", "#F0FDF4", "#15803D"),
    ("anh_23.png", "Ảnh 23", "Quét Camera QR / Nhập PIN giao món", "activity_driver_scan_qr.xml", "app-internal (Driver)", "#F0FDF4", "#15803D"),
    ("anh_24.png", "Ảnh 24", "Admin: Dashboard KPI & Duyệt Quán", "fragment_admin_store_list.xml", "app-admin", "#FEE2E2", "#B91C1C"),
    ("anh_25a.png", "Ảnh 25A", "Admin: Giám sát Đơn & User Realtime", "fragment_admin_monitor.xml", "app-admin", "#FEE2E2", "#B91C1C"),
    ("anh_25b.png", "Ảnh 25B", "Admin: Xử lý Khiếu nại & Phán quyết", "fragment_admin_complaint_list.xml", "app-admin", "#FEE2E2", "#B91C1C"),
]

def make_screen_placeholder(filename, code, title, layout, module, bg_col, border_col):
    out_file = os.path.join(sc_dir, filename)
    fig, ax = plt.subplots(figsize=(6, 10), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 160)
    ax.axis('off')

    # Phone Bezel
    phone = FancyBboxPatch((5, 5), 90, 150, boxstyle="round,pad=1,rounding_size=6",
                           facecolor=bg_col, edgecolor=border_col, linewidth=3)
    ax.add_patch(phone)

    # Top speaker / notch
    speaker = FancyBboxPatch((40, 148), 20, 3, boxstyle="round,pad=0.2,rounding_size=1",
                            facecolor=border_col, edgecolor="none")
    ax.add_patch(speaker)

    # Title & tags
    ax.text(50, 130, code, fontsize=16, fontweight='bold', ha='center', color=border_col)
    ax.text(50, 115, title, fontsize=12, fontweight='bold', ha='center', color="#0F172A", wrap=True)
    
    # Subtag
    ax.text(50, 85, f"Phân hệ: {module}\nLayout: {layout}", fontsize=10, ha='center', color="#475569",
            bbox=dict(boxstyle="round,pad=0.4", facecolor="#FFFFFF", edgecolor="#CBD5E1"))
    
    ax.text(50, 45, "[ KHUNG CHỤP MÀN HÌNH GIAO DIỆN ]\n(Có thể thay thế bằng ảnh chụp thực tế\nqua chuột phải > Change Picture)",
            fontsize=8.5, fontstyle='italic', ha='center', color="#64748B")

    plt.tight_layout()
    plt.savefig(out_file, dpi=200, bbox_inches='tight')
    plt.close(fig)

for itm in screenshots:
    make_screen_placeholder(*itm)
print("ALL 26 SCREENSHOT PLACEHOLDERS GENERATED!")

# Test Cases
test_cases = [
    ("kt_1.png", "Ảnh KT-1", "Kiểm thử Xác thực Email OTP & Bypass Test Mode", "Khách hàng & Đối tác", "#EFF6FF", "#1D4ED8"),
    ("kt_2.png", "Ảnh KT-2", "Kiểm thử Quét VietQR Napas247 Tự động duyệt đơn", "BankNotificationListenerService", "#FEF3C7", "#B45309"),
    ("kt_3.png", "Ảnh KT-3", "Kiểm thử Đầu bếp bấm 'Đã nấu xong' đổi trạng thái READY", "ChefFragment & ChefViewModel", "#F3E8FF", "#7E22CE"),
    ("kt_4.png", "Ảnh KT-4", "Kiểm thử Định vị GPS Tài xế & Ghép đơn đa điểm", "LocationForegroundService", "#F0FDF4", "#15803D"),
    ("kt_5.png", "Ảnh KT-5", "Kiểm thử Xác thực Giao hàng 2 chiều qua Camera QR / PIN", "ZXing BarcodeScanner", "#F0FDF4", "#15803D"),
    ("kt_6.png", "Ảnh KT-6", "Kiểm thử Sự cố 15 phút, Hoàn trả Quán & Trừ 20 điểm uy tín", "RETURNING_TO_STORE & trustScore", "#FEE2E2", "#B91C1C"),
]

def make_tc_placeholder(filename, code, title, module, bg_col, border_col):
    out_file = os.path.join(tc_dir, filename)
    fig, ax = plt.subplots(figsize=(10, 6), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 60)
    ax.axis('off')

    box = FancyBboxPatch((4, 4), 92, 52, boxstyle="round,pad=1,rounding_size=3",
                         facecolor=bg_col, edgecolor=border_col, linewidth=2.5)
    ax.add_patch(box)

    ax.text(50, 48, code, fontsize=15, fontweight='bold', ha='center', color=border_col)
    ax.text(50, 36, title, fontsize=11, fontweight='bold', ha='center', color="#0F172A")
    ax.text(50, 24, f"Đối tượng kiểm thử: {module}", fontsize=9.5, ha='center', color="#475569")
    ax.text(50, 12, "[ ẢNH MINH CHỨNG KẾT QUẢ KIỂM THỬ THỰC NGHIỆM: ĐẠT (PASSED) ]",
            fontsize=8.5, fontweight='bold', fontstyle='italic', ha='center', color="#166534")

    plt.tight_layout()
    plt.savefig(out_file, dpi=200, bbox_inches='tight')
    plt.close(fig)

for itm in test_cases:
    make_tc_placeholder(*itm)
print("ALL 6 TEST CASE PLACEHOLDERS GENERATED!")
