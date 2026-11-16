import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, Ellipse, Rectangle, Arrow

out_dir = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\docs\diagrams"
os.makedirs(out_dir, exist_ok=True)

plt.rcParams['font.sans-serif'] = ['DejaVu Sans', 'Arial', 'Segoe UI']
plt.rcParams['axes.edgecolor'] = '#333333'

def save_fig(fig, filename):
    path = os.path.join(out_dir, filename)
    plt.tight_layout()
    plt.savefig(path, dpi=300, bbox_inches='tight')
    plt.close(fig)
    print(f"Generated: {path}")

# ==========================================
# 1. SƠ ĐỒ 1: USE CASE 6 ROLES
# ==========================================
def generate_so_do_1():
    fig, ax = plt.subplots(figsize=(18, 13), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    # System boundary
    boundary = FancyBboxPatch((24, 3), 52, 94, boxstyle="round,pad=1,rounding_size=2",
                              facecolor="#F8FAFC", edgecolor="#1E293B", linewidth=2.5, linestyle="--")
    ax.add_patch(boundary)
    ax.text(50, 94.5, "HỆ THỐNG ĐẶT VÀ GIAO ĐỒ ĂN OISHI FOOD (6 ROLES RBAC)", 
            fontsize=15, fontweight='bold', ha='center', va='center', color="#0F172A")

    # Helper draw actor
    def draw_actor(x, y, name, sub):
        head = plt.Circle((x, y + 2.0), 1.1, color='#1E3A8A', fill=False, linewidth=2)
        ax.add_patch(head)
        ax.plot([x, x], [y + 0.9, y - 1.8], color='#1E3A8A', linewidth=2)
        ax.plot([x - 1.8, x + 1.8], [y + 0.1, y + 0.1], color='#1E3A8A', linewidth=2)
        ax.plot([x, x - 1.6], [y - 1.8, y - 3.8], color='#1E3A8A', linewidth=2)
        ax.plot([x, x + 1.6], [y - 1.8, y - 3.8], color='#1E3A8A', linewidth=2)
        ax.text(x, y - 4.8, f"<<Actor>>\n{name}", fontsize=10, fontweight='bold', ha='center', va='top', color='#0F172A')
        ax.text(x, y - 7.5, sub, fontsize=8, fontstyle='italic', ha='center', va='top', color='#475569')

    # 6 Actors
    # Trái: Customer, Chef, Driver
    draw_actor(11, 80, "Khách hàng\n(Customer)", "app-customer\n(Đặt món, Theo dõi)")
    draw_actor(11, 48, "Đầu bếp\n(Chef)", "app-internal\n(Nhận nấu, Đổi READY)")
    draw_actor(11, 18, "Tài xế\n(Driver / Shipper)", "app-internal\n(Nhận đơn, Giao món)")

    # Phải: Boss, Merchant, Admin
    draw_actor(89, 80, "Chủ chuỗi\n(Boss)", "app-internal\n(Quản lý chi nhánh)")
    draw_actor(89, 48, "Quản lý quán\n(Merchant)", "app-internal\n(Menu, Đơn Kanban)")
    draw_actor(89, 18, "Quản trị viên\n(Admin)", "admin.html\n(Duyệt quán, Cấu hình)")

    # Use Cases
    use_cases = [
        ("UC1", "Xác thực OTP & Đăng nhập phân quyền", "Chung cho 6 phân hệ", 50, 89, "#E0E7FF", "#1E1B4B"),
        ("UC2", "Duyệt Quán, Tìm kiếm Món & Gói Combo", "Lọc danh mục, Đề xuất", 37, 81, "#EFF6FF", "#1E3A8A"),
        ("UC3", "Đặt hàng & Thanh toán VietQR / COD", "Napas247 tự động duyệt", 37, 73, "#EFF6FF", "#1E3A8A"),
        ("UC4", "Theo dõi GPS Realtime & Mã QR/PIN", "Dẫn đường & Nhận món", 37, 65, "#EFF6FF", "#1E3A8A"),
        ("UC5", "Đánh giá món, Khiếu nại & Điểm uy tín", "Trừ điểm vi phạm bùng hàng", 37, 57, "#EFF6FF", "#1E3A8A"),
        
        ("UC6", "Quản lý Chuỗi, Mở Chi nhánh & Gán Merchant", "Boss phân quyền chi nhánh", 63, 81, "#FEF3C7", "#78350F"),
        ("UC7", "Xác minh Quán bằng OCR CCCD", "ML Kit bóc tách căn cước", 63, 73, "#FEF3C7", "#78350F"),
        ("UC8", "Quản lý Menu Món, Topping, Combo & VietQR", "Cấu hình ngân hàng quán", 63, 65, "#FEF3C7", "#78350F"),
        ("UC9", "Tiếp nhận Đơn hàng Kanban & Báo cáo Doanh thu", "4 tab xử lý đơn hàng", 63, 57, "#FEF3C7", "#78350F"),

        ("UC10", "Tiếp nhận Đơn nấu & Cập nhật Hoàn tất (READY)", "Chuyên khu vực Bếp", 50, 48, "#F3E8FF", "#581C87"),

        ("UC11", "Nhận đơn READY & Tối ưu Ghép đơn Đa điểm", "Batching tuyến đường", 37, 36, "#F0FDF4", "#14532D"),
        ("UC12", "Dẫn đường Google Maps & Quét Camera QR/PIN", "Xác thực giao hàng 2 chiều", 37, 26, "#F0FDF4", "#14532D"),
        ("UC13", "Báo sự cố 15 phút & Hoàn trả món về Bếp", "RETURNING_TO_STORE", 37, 16, "#F0FDF4", "#14532D"),

        ("UC14", "Kiểm duyệt Quán mới & Quản lý Danh mục sàn", "Admin duyệt hồ sơ OCR", 63, 30, "#FEE2E2", "#7F1D1D"),
        ("UC15", "Giám sát Đơn Realtime & Giải quyết Khiếu nại", "Can thiệp điểm uy tín", 63, 20, "#FEE2E2", "#7F1D1D"),
        ("UC16", "Cấu hình Sàn: Phí Vận chuyển & Bypass OTP Test", "system_config", 63, 10, "#FEE2E2", "#7F1D1D"),
    ]

    uc_pos = {}
    for ucid, title, sub, cx, cy, fill, text_col in use_cases:
        ellipse = Ellipse((cx, cy), width=21, height=5.2, facecolor=fill, edgecolor=text_col, linewidth=1.5)
        ax.add_patch(ellipse)
        ax.text(cx, cy + 0.7, f"{ucid}: {title}", fontsize=8.2, fontweight='bold', ha='center', va='center', color=text_col)
        ax.text(cx, cy - 1.1, f"({sub})", fontsize=7.2, fontstyle='italic', ha='center', va='center', color='#475569')
        uc_pos[ucid] = (cx, cy)

    lines = [
        # Customer
        ((13, 80), "UC1"), ((13, 80), "UC2"), ((13, 80), "UC3"), ((13, 80), "UC4"), ((13, 80), "UC5"),
        # Chef
        ((13, 48), "UC1"), ((13, 48), "UC10"),
        # Driver
        ((13, 18), "UC1"), ((13, 18), "UC11"), ((13, 18), "UC12"), ((13, 18), "UC13"),
        # Boss
        ((87, 80), "UC1"), ((87, 80), "UC6"), ((87, 80), "UC9"),
        # Merchant
        ((87, 48), "UC1"), ((87, 48), "UC7"), ((87, 48), "UC8"), ((87, 48), "UC9"),
        # Admin
        ((87, 18), "UC1"), ((87, 18), "UC14"), ((87, 18), "UC15"), ((87, 18), "UC16"),
    ]

    for (ax_pos, ay_pos), target_uc in lines:
        tx, ty = uc_pos[target_uc]
        dx = tx - ax_pos
        dy = ty - ay_pos
        dist = (dx**2 + dy**2)**0.5
        ax.annotate("", xy=(tx - (8.5 * dx / dist), ty - (2.2 * dy / dist)), xytext=(ax_pos, ay_pos),
                    arrowprops=dict(arrowstyle="-", color="#64748B", linewidth=1.1, alpha=0.85))

    fig.text(0.5, 0.01, "Hình 2.1: Sơ đồ Use Case tổng quát Hệ thống Oishi Food (6 Roles)", ha='center',
             fontsize=11, fontweight='bold', fontstyle='italic', color='#1E293B')
    save_fig(fig, "so_do_1_use_case.png")

# ==========================================
# 2. SƠ ĐỒ 2: LUỒNG VẬN HÀNH 6 PHÂN HỆ
# ==========================================
def generate_so_do_2():
    fig, ax = plt.subplots(figsize=(16, 11), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "SƠ ĐỒ MỐI LIÊN HỆ LUỒNG VẬN HÀNH GIỮA 6 VAI TRÒ TRONG OISHI FOOD", 
            fontsize=14, fontweight='bold', ha='center', color="#0F172A")

    def draw_box(x, y, w, h, title, items, color, border):
        box = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.5,rounding_size=1", facecolor=color, edgecolor=border, linewidth=2)
        ax.add_patch(box)
        ax.text(x + w/2, y + h - 2.5, title, fontsize=10.5, fontweight='bold', ha='center', color=border)
        for i, itm in enumerate(items):
            ax.text(x + 1.5, y + h - 5.5 - i * 3.0, itm, fontsize=8.5, ha='left', color="#1E293B")

    # 6 Blocks
    draw_box(4, 66, 28, 25, "1. KHÁCH HÀNG (app-customer)", [
        "- Chọn món / combo yêu thích",
        "- Thanh toán VietQR Napas247 / COD",
        "- Theo dõi GPS tài xế thời gian thực",
        "- Cung cấp mã QR/PIN nhận đồ ăn",
        "- Đánh giá món & Tích lũy uy tín"
    ], "#EFF6FF", "#1D4ED8")

    draw_box(36, 66, 28, 25, "2. QUÁN ĂN (app-internal)", [
        "- Boss: Mở chi nhánh, gán Merchant",
        "- Merchant: Quản lý Menu & Topping",
        "- Cấu hình ngân hàng nhận VietQR",
        "- Nhận đơn Kanban (Duyệt VietQR)",
        "- Chuyển đơn xuống Bếp nấu"
    ], "#FEF3C7", "#B45309")

    draw_box(68, 66, 28, 25, "3. ĐẦU BẾP (Chef - app-internal)", [
        "- Tiếp nhận danh sách món cần nấu",
        "- Chế biến và đóng gói đúng số lượng",
        "- Bấm 'Đã nấu xong' -> Đổi READY",
        "- Merchant không can thiệp nút Bếp",
        "- Tối ưu tốc độ phục vụ đơn"
    ], "#F3E8FF", "#7E22CE")

    draw_box(4, 15, 28, 25, "4. TÀI XẾ (Driver - app-internal)", [
        "- Nhận đơn READY tại sảnh chờ",
        "- Ghép đơn đa điểm (Batching)",
        "- Đến quán lấy món -> DELIVERING",
        "- Quét Camera QR / Nhập PIN của khách",
        "- Báo sự cố 15p: RETURNING_TO_STORE"
    ], "#F0FDF4", "#15803D")

    draw_box(36, 15, 28, 25, "5. TRUNG TÂM DỮ LIỆU FIREBASE", [
        "- RTDB: Đồng bộ tức thời trạng thái",
        "- Tracking: Lưu tọa độ GPS tài xế",
        "- Lắng nghe thông báo biến động số dư",
        "- Trừ 20 điểm uy tín khi bùng hàng",
        "- Bảo toàn tiền cọc cho Tài xế"
    ], "#F8FAFC", "#334155")

    draw_box(68, 15, 28, 25, "6. QUẢN TRỊ VIÊN (admin.html)", [
        "- Duyệt hồ sơ quán mới từ OCR CCCD",
        "- Quản lý danh mục món toàn hệ thống",
        "- Giám sát trực tiếp đơn hàng toàn sàn",
        "- Phán quyết tranh chấp khiếu nại",
        "- Cấu hình công tắc Bypass OTP Test"
    ], "#FEE2E2", "#B91C1C")

    # Arrows
    def draw_conn(p1, p2, label=""):
        ax.annotate("", xy=p2, xytext=p1, arrowprops=dict(arrowstyle="->", color="#475569", linewidth=1.8))
        if label:
            mx, my = (p1[0] + p2[0])/2, (p1[1] + p2[1])/2
            ax.text(mx, my + 1.2, label, fontsize=8, ha='center', fontweight='bold', color="#0F172A",
                    bbox=dict(boxstyle="round,pad=0.2", facecolor="white", edgecolor="#CBD5E1", alpha=0.9))

    draw_conn((32, 78), (36, 78), "Đặt đơn (VietQR)")
    draw_conn((64, 78), (68, 78), "Bếp nấu (PREPARING)")
    draw_conn((82, 66), (28, 40), "Sẵn sàng (READY)")
    draw_conn((18, 40), (18, 66), "Giao hàng (Quét QR/PIN)")
    draw_conn((32, 27), (36, 27), "Cập nhật GPS & Status")
    draw_conn((50, 40), (50, 66), "Realtime sync")
    draw_conn((68, 27), (64, 27), "Giám sát & Cấu hình")

    fig.text(0.5, 0.02, "Hình 2.2: Sơ đồ mối liên hệ luồng vận hành giữa các phân hệ trong Oishi Food", ha='center',
             fontsize=11, fontweight='bold', fontstyle='italic', color='#1E293B')
    save_fig(fig, "so_do_2_luong_van_hanh.png")

# ==========================================
# 3. SƠ ĐỒ 3: CLASS DIAGRAM
# ==========================================
def generate_so_do_3():
    fig, ax = plt.subplots(figsize=(18, 12), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "SƠ ĐỒ LỚP (CLASS DIAGRAM) HỆ THỐNG MỚI OISHI FOOD", 
            fontsize=14, fontweight='bold', ha='center', color="#0F172A")

    def draw_class(x, y, w, h, name, fields, methods):
        box = Rectangle((x, y), w, h, facecolor="#F8FAFC", edgecolor="#1E293B", linewidth=1.8)
        ax.add_patch(box)
        # Header
        header = Rectangle((x, y + h - 3.2), w, 3.2, facecolor="#E2E8F0", edgecolor="#1E293B", linewidth=1.5)
        ax.add_patch(header)
        ax.text(x + w/2, y + h - 1.8, name, fontsize=9.5, fontweight='bold', ha='center', color="#0F172A")
        # Line separating fields & methods
        mid = y + h - 3.2 - len(fields)*1.6 - 0.5
        ax.plot([x, x + w], [mid, mid], color="#94A3B8", linewidth=1)
        for i, f in enumerate(fields):
            ax.text(x + 1, y + h - 4.5 - i*1.6, f, fontsize=7.8, color="#1E293B")
        for j, m in enumerate(methods):
            ax.text(x + 1, mid - 1.5 - j*1.6, m, fontsize=7.8, fontstyle='italic', color="#0369A1")

    # Classes
    draw_class(40, 75, 20, 18, "User", [
        "+ String uid", "+ int roleId", "+ String roleCode",
        "+ String name", "+ String email", "+ String phone",
        "+ UserStatus status"
    ], ["+ getRole(): Role", "+ isLocked(): boolean"])

    draw_class(5, 75, 23, 18, "Customer", [
        "+ String customerId", "+ int trustScore",
        "+ String defaultAddress", "+ List<Address> book",
        "+ boolean isDeposited"
    ], ["+ deductScore(int pts)", "+ canOrderWithoutDeposit()"])

    draw_class(72, 75, 24, 18, "Store", [
        "+ String storeId", "+ String bossId",
        "+ String name", "+ String address",
        "+ double lat, lng", "+ boolean isApproved",
        "+ String bankId, accountNo"
    ], ["+ getVietQrCode(): String", "+ toggleOpenStatus()"])

    draw_class(72, 45, 24, 22, "Dish", [
        "+ String dishId", "+ String storeId",
        "+ String name", "+ double price",
        "+ String imageUrl", "+ boolean isAvailable",
        "+ List<DishOption> options"
    ], ["+ updateAvailability()", "+ calculatePriceWithTopping()"])

    draw_class(72, 10, 24, 25, "Order", [
        "+ String orderId", "+ String storeId",
        "+ String customerId", "+ String driverId",
        "+ String chefId", "+ OrderStatus status",
        "+ PaymentMethod method", "+ double totalPrice",
        "+ String deliveryPin", "+ String qrToken"
    ], ["+ verifyPin(String pin): bool", "+ verifyQr(String token): bool", "+ markReturningToStore()"])

    draw_class(5, 45, 23, 18, "Driver", [
        "+ String driverId", "+ String storeId",
        "+ String vehiclePlate", "+ boolean isOnline",
        "+ double currentLat, currentLng"
    ], ["+ updateLocation(lat, lng)", "+ toggleOnline(bool)"])

    draw_class(5, 15, 23, 18, "Chef", [
        "+ String chefId", "+ String storeId",
        "+ String chefName", "+ boolean isDuty"
    ], ["+ claimOrderToCook()", "+ markOrderReady()"])

    draw_class(39, 45, 22, 18, "Complaint", [
        "+ String complaintId", "+ String orderId",
        "+ String customerId", "+ String reason",
        "+ String proofImageUrl", "+ String decision"
    ], ["+ resolveByAdmin(decision)"])

    draw_class(39, 15, 22, 18, "Combo", [
        "+ String comboId", "+ String storeId",
        "+ String title", "+ double comboPrice",
        "+ List<String> dishIds"
    ], ["+ getDiscountRate()"])

    # Relationships
    def draw_rel(p1, p2, text=""):
        ax.plot([p1[0], p2[0]], [p1[1], p2[1]], color="#475569", linewidth=1.5)
        if text:
            ax.text((p1[0]+p2[0])/2, (p1[1]+p2[1])/2 + 0.8, text, fontsize=7.5, color="#1E293B", ha='center')

    draw_rel((40, 84), (28, 84), "1..1 kế thừa")
    draw_rel((40, 75), (28, 54), "1..1 kế thừa")
    draw_rel((40, 75), (28, 24), "1..1 kế thừa")
    draw_rel((72, 84), (60, 84), "sở hữu")
    draw_rel((84, 75), (84, 67), "1..* Món")
    draw_rel((84, 45), (84, 35), "1..* Đơn")
    draw_rel((28, 24), (72, 24), "vận chuyển")
    draw_rel((28, 80), (72, 28), "đặt hàng")
    draw_rel((39, 54), (72, 28), "khiếu nại")

    fig.text(0.5, 0.01, "Hình 2.3: Sơ đồ Lớp (Class Diagram) Toàn diện Hệ thống Oishi Food", ha='center',
             fontsize=11, fontweight='bold', fontstyle='italic', color='#1E293B')
    save_fig(fig, "so_do_3_class_diagram.png")

# ==========================================
# 4. SƠ ĐỒ 4: SEQUENCE DIAGRAM ĐẶT HÀNG & GIAO
# ==========================================
def generate_so_do_4():
    fig, ax = plt.subplots(figsize=(17, 12), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "SƠ ĐỒ TUẦN TỰ: ĐẶT HÀNG – DUYỆT VIETQR – BẾP NẤU – GIAO NHẬN QR/PIN", 
            fontsize=13, fontweight='bold', ha='center', color="#0F172A")

    actors = [
        ("Khách hàng", 10), ("App Customer", 25), ("Firebase RTDB", 42),
        ("Merchant", 58), ("Chef (Bếp)", 72), ("Driver (Tài xế)", 88)
    ]

    for name, x in actors:
        # Box header
        box = FancyBboxPatch((x - 6, 90), 12, 4.5, boxstyle="round,pad=0.3", facecolor="#E0E7FF", edgecolor="#1E3A8A", linewidth=1.5)
        ax.add_patch(box)
        ax.text(x, 92.2, name, fontsize=8.5, fontweight='bold', ha='center', color="#1E3A8A")
        # Lifeline
        ax.plot([x, x], [89, 5], color="#94A3B8", linestyle="--", linewidth=1.2)

    steps = [
        (1, 10, 25, "1. Chọn món & chọn VietQR", False),
        (2, 25, 25, "2. Sinh mã VietQR (Napas247)", True),
        (3, 10, 25, "3. Khách quét ngân hàng chuyển tiền", False),
        (4, 58, 42, "4. BankService bắt biến động số dư -> PAID", False),
        (5, 42, 25, "5. Báo thanh toán thành công", False),
        (6, 42, 58, "6. Đơn hiện trên Kanban (PENDING)", False),
        (7, 58, 42, "7. Merchant bấm Nhận đơn -> PREPARING", False),
        (8, 42, 72, "8. Bếp thấy đơn cần nấu", False),
        (9, 72, 72, "9. Bếp chế biến & đóng gói", True),
        (10, 72, 42, "10. Bếp bấm 'Đã nấu xong' -> READY", False),
        (11, 42, 88, "11. Đơn hiện sảnh chờ Tài xế", False),
        (12, 88, 42, "12. Tài xế bấm Nhận đơn -> ASSIGNED", False),
        (13, 88, 58, "13. Tài xế đến quán nhận món -> DELIVERING", False),
        (14, 88, 42, "14. Gửi tọa độ GPS xe chạy realtime", False),
        (15, 42, 25, "15. Vẽ Polyline xe chạy trên Maps khách", False),
        (16, 88, 10, "16. Đến điểm giao: Yêu cầu QR / PIN", False),
        (17, 10, 88, "17. Khách đưa mã QR / PIN 4 số", False),
        (18, 88, 42, "18. Quét Camera thành công -> COMPLETED", False),
        (19, 42, 25, "19. Thông báo hoàn tất & mời đánh giá", False),
    ]

    for idx, (step_num, x1, x2, msg, is_self) in enumerate(steps):
        y = 86 - idx * 4.2
        if is_self:
            ax.plot([x1, x1 + 4, x1 + 4, x1], [y + 1, y + 1, y - 1, y - 1], color="#0F766E", linewidth=1.5)
            ax.text(x1 + 5, y, msg, fontsize=7.5, ha='left', va='center', color="#0F766E")
        else:
            ax.annotate("", xy=(x2, y), xytext=(x1, y),
                        arrowprops=dict(arrowstyle="->", color="#1E293B", linewidth=1.3))
            mx = (x1 + x2) / 2
            ax.text(mx, y + 0.9, msg, fontsize=7.5, ha='center', va='bottom', color="#1E293B",
                    bbox=dict(boxstyle="round,pad=0.15", facecolor="#FFFFFF", edgecolor="none", alpha=0.85))

    fig.text(0.5, 0.01, "Hình 2.4: Sơ đồ Tuần tự Luồng Đặt hàng – Chế biến – Giao nhận mã QR/PIN", ha='center',
             fontsize=11, fontweight='bold', fontstyle='italic', color='#1E293B')
    save_fig(fig, "so_do_4_sequence_dat_hang.png")

# ==========================================
# 5. SƠ ĐỒ 5: SEQUENCE HỦY ĐƠN & HOÀN TRẢ BẾP
# ==========================================
def generate_so_do_5():
    fig, ax = plt.subplots(figsize=(17, 11), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "SƠ ĐỒ TUẦN TỰ: HỦY ĐƠN KHI ĐANG GIAO & HOÀN TRẢ BẾP (RETURNING_TO_STORE)", 
            fontsize=13, fontweight='bold', ha='center', color="#0F172A")

    actors = [
        ("Khách hàng", 12), ("Tài xế (Driver)", 32), ("App Internal", 52),
        ("Firebase RTDB", 72), ("Quán ăn (Merchant)", 90)
    ]

    for name, x in actors:
        box = FancyBboxPatch((x - 7, 90), 14, 4.5, boxstyle="round,pad=0.3", facecolor="#FEE2E2", edgecolor="#B91C1C", linewidth=1.5)
        ax.add_patch(box)
        ax.text(x, 92.2, name, fontsize=8.5, fontweight='bold', ha='center', color="#B91C1C")
        ax.plot([x, x], [89, 5], color="#94A3B8", linestyle="--", linewidth=1.2)

    steps = [
        (1, 32, 12, "1. Tài xế đến điểm giao, gọi điện cho khách", False),
        (2, 12, 32, "2. Khách không nghe máy / Bấm từ chối nhận món", False),
        (3, 32, 52, "3. Bật bộ đếm sự cố chờ khách 15 phút", False),
        (4, 52, 12, "4. Gửi thông báo khẩn cấp cảnh báo vi phạm", False),
        (5, 52, 52, "5. Đếm ngược 15:00 kết thúc", True),
        (6, 32, 52, "6. Tài xế bấm 'Báo cáo sự cố bùng hàng'", False),
        (7, 52, 72, "7. Cập nhật status = RETURNING_TO_STORE", False),
        (8, 72, 72, "8. Trừ 20 điểm uy tín (trustScore) của Khách", True),
        (9, 72, 12, "9. Khóa đơn & gửi thông báo phạt điểm", False),
        (10, 72, 90, "10. Báo quán: Tài xế đang mang món quay về", False),
        (11, 32, 90, "11. Tài xế đem thức ăn hoàn trả về quán", False),
        (12, 90, 52, "12. Quán bấm xác nhận đã nhận lại món", False),
        (13, 52, 72, "13. Chuyển status = RETURNED_AND_CANCELLED", False),
        (14, 72, 52, "14. Hoàn trả tiền cọc cho Tài xế an toàn", False),
    ]

    for idx, (step_num, x1, x2, msg, is_self) in enumerate(steps):
        y = 85 - idx * 5.5
        if is_self:
            ax.plot([x1, x1 + 5, x1 + 5, x1], [y + 1.2, y + 1.2, y - 1.2, y - 1.2], color="#DC2626", linewidth=1.5)
            ax.text(x1 + 6, y, msg, fontsize=8, ha='left', va='center', color="#DC2626")
        else:
            ax.annotate("", xy=(x2, y), xytext=(x1, y),
                        arrowprops=dict(arrowstyle="->", color="#1E293B", linewidth=1.3))
            mx = (x1 + x2) / 2
            ax.text(mx, y + 1.0, msg, fontsize=8, ha='center', va='bottom', color="#1E293B",
                    bbox=dict(boxstyle="round,pad=0.2", facecolor="#FFFFFF", edgecolor="none", alpha=0.85))

    fig.text(0.5, 0.01, "Hình 2.5: Sơ đồ Tuần tự Khách hủy đơn khi đang giao & Hoàn trả Bếp", ha='center',
             fontsize=11, fontweight='bold', fontstyle='italic', color='#1E293B')
    save_fig(fig, "so_do_5_sequence_huy_don.png")

# ==========================================
# 6. SƠ ĐỒ 6: STATE MACHINE VÒNG ĐỜI ĐƠN HÀNG
# ==========================================
def generate_so_do_6():
    fig, ax = plt.subplots(figsize=(16, 10), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 96, "SƠ ĐỒ MÁY TRẠNG THÁI VÒNG ĐỜI ĐƠN HÀNG (ORDER STATE MACHINE)", 
            fontsize=14, fontweight='bold', ha='center', color="#0F172A")

    states = [
        ("PENDING", 12, 75, "Chờ duyệt / Chờ VietQR", "#E0E7FF", "#1D4ED8"),
        ("PREPARING", 38, 75, "Quán nhận / Bếp nấu", "#FEF3C7", "#B45309"),
        ("READY", 65, 75, "Bếp nấu xong / Chờ ship", "#F3E8FF", "#7E22CE"),
        ("ASSIGNED", 88, 75, "Tài xế đã nhận cuốc", "#F0FDF4", "#15803D"),
        ("PICKED_UP", 88, 42, "Tài xế đã lấy món", "#ECFDF5", "#047857"),
        ("DELIVERING", 60, 42, "Đang giao trên đường", "#FEF08A", "#854D0E"),
        ("COMPLETED", 15, 42, "Giao thành công (QR/PIN)", "#DCFCE7", "#166534"),
        ("CANCELLED", 12, 15, "Hủy đơn sớm", "#FEE2E2", "#991B1B"),
        ("RETURNING_TO_STORE", 60, 15, "Sự cố 15p: Đang trả quán", "#FFE4E6", "#BE123C"),
        ("RETURNED_CANCELLED", 90, 15, "Đã trả quán (Trừ 20đ)", "#F1F5F9", "#475569")
    ]

    s_pos = {}
    for name, cx, cy, desc, fill, col in states:
        box = FancyBboxPatch((cx - 10, cy - 4.5), 20, 9, boxstyle="round,pad=0.4,rounding_size=1.5",
                             facecolor=fill, edgecolor=col, linewidth=2)
        ax.add_patch(box)
        ax.text(cx, cy + 1.2, name, fontsize=9.5, fontweight='bold', ha='center', color=col)
        ax.text(cx, cy - 2.0, desc, fontsize=7.5, fontstyle='italic', ha='center', color="#334155")
        s_pos[name] = (cx, cy)

    # Start and End points
    start = plt.Circle((3, 75), 1.5, color='#0F172A')
    ax.add_patch(start)
    ax.annotate("", xy=(s_pos["PENDING"][0] - 10, 75), xytext=(4.5, 75),
                arrowprops=dict(arrowstyle="->", color="#0F172A", linewidth=1.8))

    def draw_trans(s1, s2, label="", curve=0.0):
        p1 = s_pos[s1]
        p2 = s_pos[s2]
        connectionstyle = f"arc3,rad={curve}" if curve != 0.0 else "arc3,rad=0"
        ax.annotate("", xy=p2, xytext=p1,
                    arrowprops=dict(arrowstyle="->", color="#334155", linewidth=1.5, connectionstyle=connectionstyle))
        mx = (p1[0] + p2[0])/2
        my = (p1[1] + p2[1])/2 + (curve * 12)
        if label:
            ax.text(mx, my + 1.2, label, fontsize=7.8, ha='center', fontweight='bold', color="#0F172A",
                    bbox=dict(boxstyle="round,pad=0.2", facecolor="#FFFFFF", edgecolor="#CBD5E1", alpha=0.9))

    draw_trans("PENDING", "PREPARING", "Merchant nhận")
    draw_trans("PREPARING", "READY", "Chef nấu xong")
    draw_trans("READY", "ASSIGNED", "Driver nhận")
    draw_trans("ASSIGNED", "PICKED_UP", "Lấy món tại quán")
    draw_trans("PICKED_UP", "DELIVERING", "Bắt đầu giao")
    draw_trans("DELIVERING", "COMPLETED", "Quét QR / Nhập PIN")
    draw_trans("PENDING", "CANCELLED", "Hủy trước khi nấu")
    draw_trans("DELIVERING", "RETURNING_TO_STORE", "Khách bùng / Quá 15p")
    draw_trans("RETURNING_TO_STORE", "RETURNED_CANCELLED", "Quán xác nhận nhận lại")

    fig.text(0.5, 0.01, "Hình 2.6: Sơ đồ Máy trạng thái Đơn hàng trong Hệ thống Oishi Food", ha='center',
             fontsize=11, fontweight='bold', fontstyle='italic', color='#1E293B')
    save_fig(fig, "so_do_6_state_machine.png")

if __name__ == "__main__":
    generate_so_do_1()
    generate_so_do_2()
    generate_so_do_3()
    generate_so_do_4()
    generate_so_do_5()
    generate_so_do_6()
    print("ALL 6 DIAGRAMS GENERATED SUCCESSFULLY!")
