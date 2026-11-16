import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, Ellipse

# Thiết lập thư mục lưu ảnh
out_dir = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\docs\diagrams"
os.makedirs(out_dir, exist_ok=True)
out_path = os.path.join(out_dir, "so_do_1_use_case.png")

# Cấu hình font và kích thước đồ họa (rộng rãi, độ phân giải cao 300 DPI)
plt.rcParams['font.sans-serif'] = ['DejaVu Sans', 'Arial', 'Segoe UI']
plt.rcParams['axes.edgecolor'] = '#333333'

fig, ax = plt.subplots(figsize=(16, 12), dpi=300)
ax.set_xlim(0, 100)
ax.set_ylim(0, 100)
ax.axis('off')

# 1. Vẽ Ranh giới hệ thống (System Boundary)
boundary = FancyBboxPatch((24, 4), 52, 92,
                          boxstyle="round,pad=1,rounding_size=2",
                          facecolor="#F8FAFC", edgecolor="#1E293B", linewidth=2.5, linestyle="--")
ax.add_patch(boundary)

# Tiêu đề hệ thống
ax.text(50, 93, "HỆ THỐNG ĐẶT VÀ GIAO ĐỒ ĂN OISHI FOOD", 
        fontsize=15, fontweight='bold', ha='center', va='center', color="#0F172A")

# 2. Hàm vẽ Actor (Hình người UML chuẩn)
def draw_actor(x, y, name, role_desc):
    # Đầu (Head)
    head = plt.Circle((x, y + 2.5), 1.2, color='#1E3A8A', fill=False, linewidth=2)
    ax.add_patch(head)
    # Thân (Spine)
    ax.plot([x, x], [y + 1.3, y - 1.8], color='#1E3A8A', linewidth=2)
    # Tay (Arms)
    ax.plot([x - 2.0, x + 2.0], [y + 0.3, y + 0.3], color='#1E3A8A', linewidth=2)
    # Chân (Legs)
    ax.plot([x, x - 1.8], [y - 1.8, y - 4.2], color='#1E3A8A', linewidth=2)
    ax.plot([x, x + 1.8], [y - 1.8, y - 4.2], color='#1E3A8A', linewidth=2)
    # Tên Actor
    ax.text(x, y - 5.5, f"<<Actor>>\n{name}", fontsize=11, fontweight='bold', 
            ha='center', va='top', color='#0F172A')
    ax.text(x, y - 8.2, role_desc, fontsize=8.5, fontstyle='italic', 
            ha='center', va='top', color='#475569')

# Vẽ 4 Tác nhân chính
# Bên trái: Khách hàng & Tài xế
draw_actor(11, 75, "Khách hàng\n(Customer)", "app-customer\n(Đặt món, Theo dõi)")
draw_actor(11, 28, "Tài xế\n(Shipper / Driver)", "app-internal\n(Nhận đơn, Giao món)")

# Bên phải: Quán ăn/Chủ chuỗi & Quản trị viên
draw_actor(89, 75, "Quán ăn & Boss\n(Merchant / Boss)", "app-internal\n(Quản lý bếp, Menu)")
draw_actor(89, 28, "Quản trị viên\n(Admin)", "app-admin\n(Duyệt quán, Cấu hình)")

# 3. Danh sách Use Cases (Chia theo phân hệ)
# Cấu trúc: (ID, Title, Subtitle, Center_X, Center_Y, Color, TextColor)
use_cases = [
    # Nhóm Dùng chung
    ("UC1", "Đăng ký OTP / Đăng nhập", "Firebase Auth & Bypass Test", 50, 87, "#E0E7FF", "#1E1B4B"),
    
    # Nhóm Khách hàng (Customer)
    ("UC2", "Tìm kiếm Quán & Món/Combo", "Lọc danh mục, Đề xuất", 37, 78, "#EFF6FF", "#1E3A8A"),
    ("UC3", "Đặt hàng & Thanh toán", "COD / VietQR / MoMo", 37, 70, "#EFF6FF", "#1E3A8A"),
    ("UC4", "Theo dõi GPS & Mã QR/PIN", "Realtime Maps Polyline", 37, 62, "#EFF6FF", "#1E3A8A"),
    ("UC5", "Hủy đơn & Điểm uy tín", "Trừ điểm vi phạm hoàn cọc", 37, 54, "#EFF6FF", "#1E3A8A"),
    ("UC6", "Đánh giá & Khiếu nại", "Đánh giá sao, Phản ánh", 37, 46, "#EFF6FF", "#1E3A8A"),
    
    # Nhóm Quán ăn & Chuỗi (Merchant / Boss)
    ("UC7", "Xác minh Quán (OCR CCCD)", "ML Kit bóc tách CCCD", 63, 78, "#FEF3C7", "#78350F"),
    ("UC8", "Quản lý Menu, Danh mục, Combo", "Món ăn, Topping, Giảm giá", 63, 70, "#FEF3C7", "#78350F"),
    ("UC9", "Xử lý Đơn Kanban & VietQR", "Bếp chế biến, Duyệt tiền tự động", 63, 62, "#FEF3C7", "#78350F"),
    ("UC10", "Thống kê Doanh thu & Bán chạy", "Báo cáo ngày/tuần/tháng", 63, 54, "#FEF3C7", "#78350F"),
    ("UC11", "Quản lý Chuỗi & Chi nhánh", "Boss phân quyền Merchant", 63, 46, "#FEF3C7", "#78350F"),
    
    # Nhóm Tài xế (Shipper)
    ("UC12", "Nhận đơn & Ghép đơn đa điểm", "Khoảng cách, Tối ưu lộ trình", 37, 34, "#F0FDF4", "#14532D"),
    ("UC13", "Dẫn đường & Quét QR/PIN giao", "Google Maps & Xác nhận nhận món", 37, 24, "#F0FDF4", "#14532D"),
    ("UC14", "Báo sự cố & Hoàn trả món", "Chờ 15p -> RETURNING_TO_STORE", 37, 14, "#F0FDF4", "#14532D"),
    
    # Nhóm Quản trị (Admin)
    ("UC15", "Duyệt Quán & Cấu hình Phí sàn", "Quản lý danh mục toàn hệ thống", 63, 26, "#FEE2E2", "#7F1D1D"),
    ("UC16", "Giám sát Đơn & Giải quyết tranh chấp", "Khóa tài khoản, can thiệp điểm", 63, 16, "#FEE2E2", "#7F1D1D"),
]

# Vẽ các hình Oval Use Case
uc_pos = {}
for ucid, title, sub, cx, cy, fill, text_col in use_cases:
    # Vẽ Elip Use Case
    ellipse = Ellipse((cx, cy), width=20, height=5.2, 
                      facecolor=fill, edgecolor=text_col, linewidth=1.5)
    ax.add_patch(ellipse)
    # Text trong oval
    ax.text(cx, cy + 0.7, f"{ucid}: {title}", fontsize=9, fontweight='bold',
            ha='center', va='center', color=text_col)
    ax.text(cx, cy - 1.1, f"({sub})", fontsize=7.5, fontstyle='italic',
            ha='center', va='center', color='#475569')
    uc_pos[ucid] = (cx, cy)

# 4. Vẽ đường liên kết (Association Lines) giữa Actor và Use Case
lines = [
    # Customer
    ((13, 75), "UC1"), ((13, 75), "UC2"), ((13, 75), "UC3"),
    ((13, 75), "UC4"), ((13, 75), "UC5"), ((13, 75), "UC6"),
    
    # Merchant / Boss
    ((87, 75), "UC1"), ((87, 75), "UC7"), ((87, 75), "UC8"),
    ((87, 75), "UC9"), ((87, 75), "UC10"), ((87, 75), "UC11"),
    
    # Shipper
    ((13, 28), "UC1"), ((13, 28), "UC12"), ((13, 28), "UC13"), ((13, 28), "UC14"),
    
    # Admin
    ((87, 28), "UC15"), ((87, 28), "UC16")
]

for (ax_pos, ay_pos), target_uc in lines:
    tx, ty = uc_pos[target_uc]
    # Tính điểm chạm biên oval
    dx = tx - ax_pos
    dy = ty - ay_pos
    dist = (dx**2 + dy**2)**0.5
    # Thu hẹp điểm cuối để đường vẽ dừng ở mép oval
    offset = 9.5 if dx > 0 else -9.5
    ax.annotate("", xy=(tx - (8.5 * dx / dist), ty - (2.2 * dy / dist)), 
                xytext=(ax_pos, ay_pos),
                arrowprops=dict(arrowstyle="-", color="#64748B", linewidth=1.2, alpha=0.85))

# Chú thích chân trang đồ án
fig.text(0.5, 0.015, "Hình 2.1: Sơ đồ Use Case tổng quát Hệ thống Oishi Food (Đồ án Chuyên đề Tốt nghiệp)", 
         ha='center', fontsize=11, fontweight='bold', fontstyle='italic', color='#1E293B')

plt.tight_layout()
plt.savefig(out_path, dpi=300, bbox_inches='tight')
plt.close()
print("SUCCESS: Generated", out_path)
