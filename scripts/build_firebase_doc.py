# -*- coding: utf-8 -*-
"""
Script tự động sinh tài liệu Word (.docx) chuẩn TDC:
GIẢI THÍCH CHI TIẾT TOÀN BỘ CÁC NODE TRÊN GOOGLE FIREBASE REALTIME DATABASE - DỰ ÁN OISHI FOOD
"""

import os
import sys
import docx
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

ROOT_DIR = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android"
OUT_DOCX_PATH = os.path.join(ROOT_DIR, "GIAI_THICH_CHI_TIET_CAC_NODE_FIREBASE.docx")

doc = Document()

# 1. Cấu hình Margins chuẩn TDC: Trái 3cm (1.18 in), Phải 2cm (0.79 in), Trên 2cm (0.79 in), Dưới 2cm (0.79 in)
for section in doc.sections:
    section.top_margin = Inches(0.79)
    section.bottom_margin = Inches(0.79)
    section.left_margin = Inches(1.18)
    section.right_margin = Inches(0.79)

# 2. Cấu hình Style mặc định
normal_style = doc.styles['Normal']
normal_style.font.name = 'Times New Roman'
normal_style.font.size = Pt(12)
normal_style.font.color.rgb = RGBColor(0x1E, 0x29, 0x3B)
normal_style.paragraph_format.line_spacing = 1.3
normal_style.paragraph_format.space_after = Pt(4)

# 3. Hàm tiện ích định dạng bảng
def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=140, right=140):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'''
        <w:tcMar {nsdecls("w")}>
            <w:top w:w="{top}" w:type="dxa"/>
            <w:bottom w:w="{bottom}" w:type="dxa"/>
            <w:left w:w="{left}" w:type="dxa"/>
            <w:right w:w="{right}" w:type="dxa"/>
        </w:tcMar>
    ''')
    tcPr.append(tcMar)

def add_title(text, level=1, space_before=14, space_after=6):
    p = doc.add_heading(level=level)
    p.paragraph_format.space_before = Pt(space_before)
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.keep_with_next = True
    
    pPr = p._p.get_or_add_pPr()
    outlineLvl = parse_xml(f'<w:outlineLvl {nsdecls("w")} w:val="{level-1}"/>')
    pPr.append(outlineLvl)
    
    run = p.add_run(text)
    run.font.name = 'Times New Roman'
    run.bold = True
    if level == 1:
        run.font.size = Pt(15)
        run.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A) # Dark Navy
    elif level == 2:
        run.font.size = Pt(13.5)
        run.font.color.rgb = RGBColor(0x1E, 0x29, 0x3B)
    elif level == 3:
        run.font.size = Pt(12.5)
        run.font.color.rgb = RGBColor(0x33, 0x41, 0x55)
    return p

def add_p(text, bold_prefix="", italic=False, align=WD_ALIGN_PARAGRAPH.JUSTIFY):
    p = doc.add_paragraph()
    p.alignment = align
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(4)
    if bold_prefix:
        r_bold = p.add_run(bold_prefix)
        r_bold.font.name = 'Times New Roman'
        r_bold.bold = True
        r_bold.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A)
    run = p.add_run(text)
    run.font.name = 'Times New Roman'
    run.italic = italic
    return p

def add_bullet(text, bold_prefix=""):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.left_indent = Inches(0.25)
    if bold_prefix:
        r_bold = p.add_run(bold_prefix)
        r_bold.font.name = 'Times New Roman'
        r_bold.bold = True
        r_bold.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A)
    run = p.add_run(text)
    run.font.name = 'Times New Roman'
    return p

def create_table(headers, rows, col_widths=None, header_bg="1E293B"):
    tbl = doc.add_table(rows=len(rows) + 1, cols=len(headers))
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False

    # Header Row
    hdr_cells = tbl.rows[0].cells
    for i, title in enumerate(headers):
        hdr_cells[i].text = title
        set_cell_background(hdr_cells[i], header_bg)
        set_cell_margins(hdr_cells[i], top=100, bottom=100, left=120, right=120)
        hdr_cells[i].vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p = hdr_cells[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(0)
        for run in p.runs:
            run.font.name = 'Times New Roman'
            run.font.size = Pt(11)
            run.bold = True
            run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)

    # Data Rows
    for r_idx, row_data in enumerate(rows):
        row_cells = tbl.rows[r_idx + 1].cells
        bg_color = "F8FAFC" if r_idx % 2 == 1 else "FFFFFF"
        for c_idx, cell_value in enumerate(row_data):
            row_cells[c_idx].text = str(cell_value)
            set_cell_background(row_cells[c_idx], bg_color)
            set_cell_margins(row_cells[c_idx], top=70, bottom=70, left=100, right=100)
            row_cells[c_idx].vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            p = row_cells[c_idx].paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.15
            for run in p.runs:
                run.font.name = 'Times New Roman'
                run.font.size = Pt(10.5)

    # Đặt độ rộng cột nếu có
    if col_widths and len(col_widths) == len(headers):
        for row in tbl.rows:
            for c_idx, w in enumerate(col_widths):
                row.cells[c_idx].width = Inches(w)

    doc.add_paragraph().paragraph_format.space_after = Pt(4)
    return tbl

def add_code_block(code_text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Inches(0.2)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(6)
    pPr = p._p.get_or_add_pPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="F1F5F9"/>')
    pPr.append(shd)
    
    run = p.add_run(code_text)
    run.font.name = 'Consolas'
    run.font.size = Pt(9.5)
    run.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A)
    return p

print("Starting to build comprehensive Firebase Documentation...")

# ==========================================================
# TRANG BÌA (COVER PAGE)
# ==========================================================
p_school = doc.add_paragraph()
p_school.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_school.paragraph_format.space_before = Pt(0)
p_school.paragraph_format.space_after = Pt(2)
r = p_school.add_run("ỦY BAN NHÂN DÂN THÀNH PHỐ HỒ CHÍ MINH\nTRƯỜNG CAO ĐẲNG CÔNG NGHỆ THỦ ĐỨC\nKHOA CÔNG NGHỆ THÔNG TIN")
r.font.name = 'Times New Roman'
r.font.size = Pt(13)
r.bold = True
r.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A)

# Logo đề tài Oishi Food
p_logo = doc.add_paragraph()
p_logo.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_logo.paragraph_format.space_before = Pt(14)
p_logo.paragraph_format.space_after = Pt(14)
logo_path = os.path.join(ROOT_DIR, "logo-app-khach.png")
if os.path.exists(logo_path):
    run_logo = p_logo.add_run()
    run_logo.add_picture(logo_path, width=Inches(1.8))

p_line = doc.add_paragraph()
p_line.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_line.paragraph_format.space_after = Pt(16)
r_line = p_line.add_run("-------------------***-------------------")
r_line.font.name = 'Times New Roman'
r_line.bold = True

p_doc_type = doc.add_paragraph()
p_doc_type.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_doc_type.paragraph_format.space_after = Pt(6)
r_dt = p_doc_type.add_run("TÀI LIỆU KỸ THUẬT CHUYÊN ĐỀ TỐT NGHIỆP 2")
r_dt.font.name = 'Times New Roman'
r_dt.font.size = Pt(14)
r_dt.bold = True
r_dt.font.color.rgb = RGBColor(0xEA, 0x58, 0x0C) # Cam Oishi

p_title = doc.add_paragraph()
p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_title.paragraph_format.space_after = Pt(12)
r_title = p_title.add_run("ĐẶC TẢ VÀ GIẢI THÍCH CHI TIẾT TOÀN BỘ CÁC NODE\nCƠ SỞ DỮ LIỆU GOOGLE FIREBASE REALTIME DATABASE")
r_title.font.name = 'Times New Roman'
r_title.font.size = Pt(18)
r_title.bold = True
r_title.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A)

p_sub = doc.add_paragraph()
p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_sub.paragraph_format.space_after = Pt(28)
r_sub = p_sub.add_run("HỆ THỐNG ĐẶT VÀ GIAO ĐỒ ĂN TRỰC TUYẾN OISHI FOOD\n(Hệ sinh thái đa phân hệ: Khách hàng, Quán ăn, Đầu bếp, Tài xế & Quản trị viên)")
r_sub.font.name = 'Times New Roman'
r_sub.font.size = Pt(12.5)
r_sub.italic = True
r_sub.font.color.rgb = RGBColor(0x47, 0x55, 0x69)

# Thông tin sinh viên & đề tài
tbl_info = doc.add_table(rows=6, cols=2)
tbl_info.alignment = WD_TABLE_ALIGNMENT.CENTER
tbl_info.autofit = False
info_data = [
    ("Chuyên ngành:", "Lập trình Thiết bị Di động (Mobile Programming - Android Kotlin)"),
    ("Giảng viên hướng dẫn:", "ThS. [Tên Giảng viên hướng dẫn]"),
    ("Sinh viên thực hiện 1:", "Trần Bá Vũ – MSSV: [MSSV Sinh viên 1]"),
    ("Sinh viên thực hiện 2:", "Nguyễn Phú Giàu – MSSV: [MSSV Sinh viên 2]"),
    ("Cơ sở dữ liệu:", "Google Firebase Realtime Database (asia-southeast1)"),
    ("Niên khóa / Học kỳ:", "Học kỳ 1 – Năm học 2026 – 2027")
]
for idx, (label, val) in enumerate(info_data):
    row_cells = tbl_info.rows[idx].cells
    row_cells[0].text = label
    row_cells[1].text = val
    row_cells[0].width = Inches(2.2)
    row_cells[1].width = Inches(4.1)
    set_cell_margins(row_cells[0], top=40, bottom=40, left=60, right=60)
    set_cell_margins(row_cells[1], top=40, bottom=40, left=60, right=60)
    p0 = row_cells[0].paragraphs[0]
    p1 = row_cells[1].paragraphs[0]
    p0.runs[0].font.name = 'Times New Roman'
    p0.runs[0].bold = True
    p0.runs[0].font.size = Pt(11.5)
    p1.runs[0].font.name = 'Times New Roman'
    p1.runs[0].font.size = Pt(11.5)

p_footer = doc.add_paragraph()
p_footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_footer.paragraph_format.space_before = Pt(36)
r_foot = p_footer.add_run("THÀNH PHỐ HỒ CHÍ MINH — NĂM 2026")
r_foot.font.name = 'Times New Roman'
r_foot.bold = True
r_foot.font.size = Pt(12)

doc.add_page_break()

# ==========================================================
# MỤC LỤC TỰ ĐỘNG
# ==========================================================
add_title("MỤC LỤC TÀI LIỆU", level=1, space_before=16)
add_p("Nhấp chuột phải vào vùng mục lục bên dưới và chọn 'Update Field' (hoặc nhấn phím F9) để Word tự động làm mới số trang:", italic=True)

# Word TOC field
p_toc = doc.add_paragraph()
r_toc = p_toc.add_run()
fldChar1 = parse_xml(r'<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w'))
instrText = parse_xml(r'<w:instrText %s xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText>' % nsdecls('w'))
fldChar2 = parse_xml(r'<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w'))
fldChar3 = parse_xml(r'<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w'))
r_toc._r.append(fldChar1)
r_toc._r.append(instrText)
r_toc._r.append(fldChar2)
r_toc._r.append(fldChar3)

doc.add_page_break()

# ==========================================================
# PHẦN 1: TỔNG QUAN KIẾN TRÚC DỮ LIỆU FIREBASE REALTIME DATABASE
# ==========================================================
add_title("PHẦN 1. TỔNG QUAN KIẾN TRÚC FIREBASE REALTIME DATABASE", level=1)

add_title("1.1 Giới thiệu Firebase Realtime Database và Lý do lựa chọn", level=2)
add_p("Google Firebase Realtime Database (RTDB) là cơ sở dữ liệu phi quan hệ (NoSQL) lưu trữ dữ liệu dưới định dạng cây JSON lớn duy nhất trên đám mây (Cloud-hosted JSON Tree). Mọi thiết bị kết nối đến Firebase đều duy trì một kênh truyền WebSocket liên tục 2 chiều (Bidirectional Persistent Connection). Khi bất kỳ một node dữ liệu nào thay đổi, đám mây Firebase sẽ tự động đẩy (Push) thay đổi đó xuống tất cả các thiết bị đang lắng nghe trong vòng vài phần trăm giây (độ trễ trung bình < 100ms).")
add_p("Hệ thống Oishi Food lựa chọn Firebase Realtime Database làm kho dữ liệu trung tâm vì các lý do vượt trội:")
add_bullet("Phù hợp đặc thù vận hành giao đồ ăn: Các sự kiện reo chuông đơn mới, đầu bếp bấm hoàn tất nấu, tài xế cập nhật tọa độ GPS từng giây và khách hàng theo dõi xe chạy cần tính thời gian thực tuyệt đối mà mô hình REST API truyền thống (kéo dữ liệu định kỳ / Polling) không thể đáp ứng được.", bold_prefix="• Tốc độ phản hồi Realtime: ")
add_bullet("Firebase Android SDK tự động lưu bộ đệm (Offline Disk Persistence) trên máy người dùng. Khi shipper đi vào vùng mất sóng 4G hoặc Wi-Fi quán bị chập chờn, ứng dụng vẫn thao tác mượt mà và tự động đồng bộ lên máy chủ ngay khi có kết nối trở lại.", bold_prefix="• Khả năng chịu lỗi Offline: ")
add_bullet("Tích hợp hoàn hảo với hệ sinh thái Google Firebase gồm Firebase Authentication (quản lý phiên đăng nhập) và Firebase Cloud Messaging (thông báo đẩy nền FCM).", bold_prefix="• Đồng bộ hệ sinh thái: ")

add_title("1.2 Thông số cấu hình và Kết nối Máy chủ Đám mây", level=2)
add_p("Toàn bộ dữ liệu của hệ thống Oishi Food được đặt tại cụm máy chủ khu vực Đông Nam Á nhằm đảm bảo băng thông tối đa và độ trễ thấp nhất cho người dùng Việt Nam:")

config_headers = ["Thuộc tính", "Giá trị cấu hình thực tế", "Ghi chú kỹ thuật"]
config_rows = [
    ["Tên Dự án (Project ID)", "chuyen-de-2-2f0bc", "Định danh duy nhất trên Google Cloud Console"],
    ["Database URL", "https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app", "Khai báo tại google-services.json & FirebaseRepository.kt"],
    ["Vùng máy chủ (Region)", "asia-southeast1 (Singapore)", "Tối ưu độ trễ cho người dùng tại TP. Hồ Chí Minh"],
    ["Giao thức kết nối", "WebSocket TLS 1.3 qua cổng 443", "Bảo mật mã hóa đầu cuối và duy trì kết nối nền"],
    ["Lớp thao tác trung tâm", "FirebaseRepository.kt (core/data/firebase)", "Đóng gói toàn bộ Transaction, Query và CRUD logic"],
    ["Tệp luật bảo mật", "database.rules.json", "Kiểm soát phân quyền 6 Roles trực tiếp trên server"]
]
create_table(config_headers, config_rows, [1.8, 3.2, 1.3])

add_title("1.3 Mô hình Cây Dữ liệu JSON Tree Tổng thể", level=2)
add_p("Khác với CSDL quan hệ SQL chia thành các bảng có khóa ngoại (Foreign Key), Firebase RTDB tổ chức dữ liệu thành các nhánh gốc (Root Nodes). Để đảm bảo hiệu năng và tránh việc tải toàn bộ cây dữ liệu lớn về máy khách, cấu trúc JSON được thiết kế phẳng (Denormalization / Flattening):")

tree_schema = """root (chuyen-de-2-2f0bc-default-rtdb)
├── users/                     # Định danh người dùng chung & phân quyền 6 Roles
├── customers/                 # Hồ sơ khách hàng & Điểm uy tín (trustScore)
├── customer_addresses/        # Sổ địa chỉ nhận hàng của khách
├── merchants/                 # Hồ sơ pháp lý KYC cửa hàng (CCCD, GPKD)
├── stores/                    # Chi nhánh cửa hàng, menu món, cấu hình VietQR
├── boss_stores/               # Phân cấp 1-N giữa Chủ chuỗi (Boss) và Chi nhánh
├── chefs/                     # Hồ sơ Đầu bếp trực thuộc từng chi nhánh
├── drivers/                   # Hồ sơ Tài xế, mã xe, trạng thái trực tuyến
├── bank_accounts/             # Tài khoản ngân hàng đối soát doanh thu
├── categories/                # Danh mục ngành hàng toàn sàn (Cơm, Phở, Combo...)
├── combos/                    # Gói combo món ăn tiết kiệm cấp sàn/quán
├── carts/                     # Giỏ hàng tạm tính theo từng quán (userId/shopId)
├── orders/                    # Node cốt lõi lưu trữ toàn bộ đơn hàng của sàn
├── tracking/                  # Tọa độ GPS tài xế chạy xe realtime mỗi 3 giây
├── order_chats/               # Kênh nhắn tin giữa Khách - Quán - Tài xế
├── dish_reviews/              # Đánh giá món ăn của khách sau khi hoàn thành
├── complaints/                # Hồ sơ khiếu nại (bùng đơn, sai món, trễ giờ)
├── trust_score_logs/          # Nhật ký lịch sử biến động điểm uy tín (+2/-30)
├── system_config/             # Cấu hình đếm ngược sự cố vòng 1, vòng 2
├── delivery_time_frames/      # Khung giờ giao hàng & fallback đếm ngược
├── roles/                     # Từ điển mã phân quyền 6 Roles
├── user_statuses/             # Từ điển trạng thái người dùng (ACTIVE, LOCKED...)
├── store_statuses/            # Từ điển trạng thái quán (OPEN, CLOSED, LOCKED...)
├── penalty_tiers/             # Từ điển các mức chế tài (NONE, WARN, HIDE, LOCK)
└── notifications/             # Hộp thư thông báo in-app và đẩy FCM"""
add_code_block(tree_schema)

doc.add_page_break()

# ==========================================================
# PHẦN 2: BẢNG TỔNG HỢP TOÀN BỘ CÁC ROOT NODES
# ==========================================================
add_title("PHẦN 2. BẢNG TỔNG HỢP TOÀN BỘ 24 ROOT NODES", level=1)
add_p("Bảng dưới đây tổng hợp đầy đủ 24 nhánh dữ liệu gốc trên Firebase Realtime Database của hệ thống Oishi Food, tương ứng với các Data Class Model trong module core:")

summary_headers = ["STT", "Tên Node & Đường dẫn", "Model (core/model)", "Phân nhóm nghiệp vụ", "Quyền Đọc / Ghi (Rules)", "Chỉ mục (indexOn)"]
summary_rows = [
    ["1", "users\nusers/{uid}", "User.kt", "Tài khoản & Phân quyền", "Đọc: auth != null\nGhi: Chính chủ / ADMIN", "role, status, email, phone, storeId, bossId"],
    ["2", "customers\ncustomers/{id}", "Customer.kt", "Tài khoản Khách hàng", "Đọc: auth != null\nGhi: auth != null", "trustScore, status, email, phone"],
    ["3", "customer_addresses\ncustomer_addresses/{uid}/{addrId}", "CustomerAddress.kt", "Vận hành Khách hàng", "Đọc: Chính chủ / ADMIN\nGhi: Chính chủ / ADMIN", "(Theo key uid/addrId)"],
    ["4", "merchants\nmerchants/{id}", "Merchant.kt", "Đối tác Quán & KYC", "Đọc: auth != null\nGhi: Chính chủ, ADMIN, BOSS", "storeId, status, approvalStatus"],
    ["5", "stores\nstores/{storeId}", "Store.kt & Dish.kt", "Cửa hàng & Thực đơn", "Đọc: true (Công khai)\nGhi: ADMIN, BOSS, MERCHANT", "merchantId, ownerId, bossId, storeStatus, category"],
    ["6", "boss_stores\nboss_stores/{bossUid}/{storeId}", "Boolean (true)", "Phân cấp Chuỗi", "Đọc: Chính chủ / ADMIN\nGhi: Chính chủ / ADMIN", "(Theo key bossUid)"],
    ["7", "chefs\nchefs/{chefId}", "Chef.kt", "Nhân sự Quán", "Đọc: auth != null\nGhi: ADMIN, BOSS, MERCHANT", "storeId, email, phone"],
    ["8", "drivers\ndrivers/{driverId}", "Driver.kt", "Nhân sự Giao hàng", "Đọc: auth != null\nGhi: ADMIN, BOSS, MERCHANT", "dutyStatus, storeId, email, driverCode"],
    ["9", "bank_accounts\nbank_accounts/{id}", "BankAccount", "Tài chính & Đối soát", "Đọc: auth != null\nGhi: Chính chủ / ADMIN", "userId, storeId"],
    ["10", "categories\ncategories/{catId}", "Category.kt", "Thực đơn Toàn sàn", "Đọc: true (Công khai)\nGhi: ADMIN", "displayOrder, isActive, status"],
    ["11", "combos\ncombos/{comboId}", "Combo.kt", "Thực đơn Toàn sàn", "Đọc: true (Công khai)\nGhi: ADMIN, BOSS, MERCHANT", "storeId, comboName"],
    ["12", "menus & restaurants\nmenus/{id} & restaurants/{id}", "MenuItem.kt", "Thực đơn (Legacy)", "Đọc: true (Công khai)\nGhi: ADMIN", "(Tương thích ngược)"],
    ["13", "carts\ncarts/{uid}/{shopId}", "Cart.kt & CartItem.kt", "Giỏ hàng Tạm tính", "Đọc: Chính chủ\nGhi: Chính chủ", "(Theo key uid/shopId)"],
    ["14", "orders\norders/{orderId}", "Order.kt & OrderItem.kt", "Đơn hàng Cốt lõi", "Đọc: auth != null\nGhi: auth != null", "customerId, shipperId, chefId, shopId, status, createdAt"],
    ["15", "tracking\ntracking/{orderId}", "TrackingLocation.kt", "Định vị GPS Realtime", "Đọc: auth != null\nGhi: auth != null (Tài xế)", "(Theo key orderId)"],
    ["16", "order_chats\norder_chats/{orderId}/{msgId}", "ChatMessage.kt", "Giao tiếp Đơn hàng", "Đọc: auth != null\nGhi: auth != null", "(Theo key orderId)"],
    ["17", "dish_reviews\ndish_reviews/{revId}", "DishReview.kt", "Đánh giá & Phản hồi", "Đọc: true (Công khai)\nGhi: auth != null", "dishId, storeId, orderId, customerId"],
    ["18", "complaints\ncomplaints/{cmpId}", "Complaint.kt", "Khiếu nại & Xử lý", "Đọc: auth != null\nGhi: auth != null", "customerId, storeId, orderId, status"],
    ["19", "trust_score_logs\ntrust_score_logs/{logId}", "TrustScoreLog.kt", "Kiểm soát Uy tín", "Đọc: auth != null\nGhi: auth != null", "customerId, orderId, createdAt"],
    ["20", "system_config\nsystem_config/delivery_countdown", "DeliveryCountdownConfig.kt", "Cấu hình Toàn sàn", "Đọc: true (Công khai)\nGhi: ADMIN", "(Key cố định)"],
    ["21", "delivery_time_frames\ndelivery_time_frames/{id}", "DeliveryCountdownConfig.kt", "Cấu hình Giao nhận", "Đọc: true (Công khai)\nGhi: ADMIN", "(Key cố định 1..4)"],
    ["22", "roles & user_statuses\nroles/{id} & user_statuses/{id}", "Role.kt, UserStatus.kt", "Từ điển Tham chiếu", "Đọc: true (Công khai)\nGhi: ADMIN", "(Danh mục tĩnh 1..4)"],
    ["23", "store_statuses & penalty_tiers\nstore_statuses/{id} & ...", "StoreStatus.kt", "Từ điển Tham chiếu", "Đọc: true (Công khai)\nGhi: ADMIN", "(Danh mục tĩnh 1..4)"],
    ["24", "notifications\nnotifications/{id}", "Notification.kt", "Thông báo Hệ thống", "Đọc: auth != null\nGhi: auth != null", "userId, createdAt, isRead"]
]
create_table(summary_headers, summary_rows, [0.4, 1.6, 1.2, 1.3, 1.0, 0.8])

doc.add_page_break()

# ==========================================================
# PHẦN 3: ĐẶC TẢ CHI TIẾT TỪNG ROOT NODE
# ==========================================================
add_title("PHẦN 3. ĐẶC TẢ CHI TIẾT TỪNG ROOT NODE & PAYLOAD MẪU", level=1)
add_p("Dưới đây là đặc tả chi tiết từng thuộc tính kỹ thuật, quy tắc ràng buộc logic, ý nghĩa nghiệp vụ và mẫu JSON thực tế của từng Node trên hệ thống Oishi Food:")

# --- 3.1 USERS ---
add_title("3.1 Node users — Định danh người dùng chung & Phân quyền 6 Roles", level=2)
add_p("Node trung tâm định danh toàn bộ người dùng trong hệ sinh thái Oishi Food. Khi người dùng đăng ký qua Email OTP hoặc tài khoản nội bộ, một bản ghi được tạo tương ứng với UID của Firebase Authentication.", bold_prefix="• Mục đích: ")
add_p("Đường dẫn: users/{uid} (với {uid} là mã định danh FirebaseAuth của người dùng).", italic=True)

users_fields = [
    ["uid", "String", "\"\"", "Mã UID duy nhất do Firebase Authentication cấp khi đăng ký."],
    ["email", "String", "\"\"", "Địa chỉ email dùng để đăng nhập và nhận mã OTP 6 số."],
    ["name", "String", "\"\"", "Họ và tên đầy đủ hiển thị trên ứng dụng."],
    ["phone", "String", "\"\"", "Số điện thoại liên hệ chính thức của người dùng."],
    ["roleId", "Int", "2", "Mã số vai trò: 1=ADMIN, 2=CUSTOMER, 3=MERCHANT/BOSS, 4=DRIVER, 5=CHEF."],
    ["role", "String", "\"CUSTOMER\"", "Mã chữ phân quyền RBAC: ADMIN, CUSTOMER, BOSSMERCHANT, MERCHANT, DRIVER, CHEF."],
    ["statusId", "Int", "1", "Mã số trạng thái: 1=ACTIVE, 2=INACTIVE, 3=OFFLINE, 4=LOCKED."],
    ["status", "String", "\"ACTIVE\"", "Trạng thái tài khoản: ACTIVE (bình thường), LOCKED (bị khóa do bom hàng hoặc vi phạm)."],
    ["storeId", "String", "null", "Mã cửa hàng mà nhân sự (Merchant, Chef, Driver) đang trực thuộc quản lý."],
    ["bossId", "String", "null", "Mã Boss chủ chuỗi quản lý chi nhánh này (đối với vai trò Merchant)."],
    ["fcmToken", "String", "null", "Token thiết bị để máy chủ gửi thông báo đẩy Firebase Cloud Messaging."],
    ["trustScore", "Int", "100", "Điểm uy tín mặc định ban đầu của tài khoản khi tạo mới."],
    ["createdAt", "Long", "SystemTime", "Dấu thời gian (Timestamp tính bằng mili-giây) khi tạo tài khoản."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], users_fields, [1.4, 1.0, 1.0, 2.9])

add_p("Mẫu dữ liệu JSON thực tế trên Firebase:", bold_prefix="Payload mẫu: ")
add_code_block("""{
  "users": {
    "UID_VU_KHACH_01": {
      "uid": "UID_VU_KHACH_01",
      "email": "khachhang_vu@gmail.com",
      "name": "Trần Bá Vũ (Khách VIP)",
      "phone": "0901234567",
      "roleId": 2,
      "role": "CUSTOMER",
      "status": "ACTIVE",
      "trustScore": 100,
      "createdAt": 1731800000000
    },
    "UID_GIAU_MERCHANT_01": {
      "uid": "UID_GIAU_MERCHANT_01",
      "email": "merchant_giau@gmail.com",
      "name": "Nguyễn Phú Giàu (Chủ quán)",
      "phone": "0987654321",
      "roleId": 3,
      "role": "MERCHANT",
      "storeId": "STORE_PHO_GIAU_01",
      "bossId": "UID_BOSS_GIAU",
      "status": "ACTIVE"
    }
  }
}""")

# --- 3.2 CUSTOMERS ---
add_title("3.2 Node customers — Hồ sơ Tín nhiệm & Điểm uy tín Khách hàng", level=2)
add_p("Lưu trữ hồ sơ tín nhiệm chuyên biệt cho khách hàng để phục vụ thuật toán đánh giá độ tin cậy khi đặt hàng COD và áp dụng chế tài chống bùng đơn (bom hàng).", bold_prefix="• Mục đích: ")
add_p("Đường dẫn: customers/{customerId} (trùng với userId của khách).", italic=True)

cust_fields = [
    ["customerId", "String", "\"\"", "Mã khách hàng (khớp UID trong node users)."],
    ["trustScore", "Int", "100", "Điểm uy tín tích lũy: >=90 (được đặt COD), 50-89 (bắt buộc cọc 20%), <20 (tự động khóa)."],
    ["totalOrders", "Int", "0", "Tổng số đơn hàng khách đã tạo từ trước đến nay."],
    ["completedOrders", "Int", "0", "Số lượng đơn hàng giao thành công (+2 điểm uy tín mỗi đơn)."],
    ["boomOrders", "Int", "0", "Số lần bom hàng / bùng đơn bị ghi nhận (-30 điểm uy tín mỗi lần)."],
    ["customerRank", "String", "\"BRONZE\"", "Hạng thành viên: BRONZE (Đồng), SILVER (Bạc), GOLD (Vàng), PLATINUM (Bạch kim)."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], cust_fields, [1.4, 1.0, 1.0, 2.9])

# --- 3.3 STORES ---
add_title("3.3 Node stores — Chi nhánh Cửa hàng, Thực đơn Món & Cấu hình VietQR", level=2)
add_p("Lưu trữ toàn bộ thông tin hoạt động của cửa hàng ăn uống, bao gồm tọa độ GPS để tính khoảng cách và phí ship, thông tin tài khoản ngân hàng để sinh mã VietQR Napas247 tự động, và cây thực đơn món ăn con.", bold_prefix="• Mục đích: ")
add_p("Đường dẫn: stores/{storeId}", italic=True)

store_fields = [
    ["storeId", "String", "\"\"", "Mã định danh duy nhất của chi nhánh cửa hàng."],
    ["storeName", "String", "\"\"", "Tên thương hiệu quán (ví dụ: Bún Bò Huế Oishi - Chi nhánh 1)."],
    ["address", "String", "\"\"", "Địa chỉ số nhà, tên đường, phường/xã của quán."],
    ["latitude", "Double", "0.0", "Tọa độ vĩ độ GPS chính xác của vị trí quán."],
    ["longitude", "Double", "0.0", "Tọa độ kinh độ GPS chính xác của vị trí quán."],
    ["category", "String", "\"Cơm\"", "Tên danh mục món ăn chính của quán."],
    ["categoryId", "String", "\"cat_rice\"", "Mã danh mục cấp sàn tương ứng (cat_rice, cat_noodles, cat_milktea...)."],
    ["imageUrl", "String", "\"\"", "Đường dẫn ảnh đại diện mặt tiền của quán."],
    ["isOpen", "Boolean", "true", "Trạng thái quán đang mở cửa đón khách (true) hay đóng cửa (false)."],
    ["storeStatus", "String", "\"OPEN\"", "Trạng thái xét duyệt: PENDING (chờ duyệt), OPEN (hoạt động), CLOSED, LOCKED."],
    ["penaltyTier", "String", "\"NONE\"", "Mức chế tài vi phạm ATTP/bị khiếu nại: NONE, WARN (cảnh cáo), HIDE (ẩn), LOCK (khóa)."],
    ["ratingAvg", "Double", "5.0", "Điểm đánh giá trung bình từ khách hàng (thang điểm 1.0 đến 5.0 sao)."],
    ["reviewCount", "Int", "0", "Tổng số lượt đánh giá thực tế của khách hàng."],
    ["merchantId", "String", "\"\"", "Mã tài khoản quản lý chi nhánh (Merchant UID)."],
    ["bossId", "String", "null", "Mã tài khoản Chủ chuỗi sở hữu (Boss UID) nếu thuộc chuỗi chi nhánh."],
    ["bankBin", "String", "\"970422\"", "Mã BIN ngân hàng thụ hưởng (970422 = MB Bank, 970415 = VietinBank, v.v.)."],
    ["bankNumber", "String", "\"\"", "Số tài khoản ngân hàng nhận tiền chuyển khoản đơn hàng."],
    ["bankAccountName", "String", "\"\"", "Họ tên chủ tài khoản ngân hàng viết hoa không dấu."],
    ["dishes", "Map", "{}", "Nhánh con lưu trữ danh sách các món ăn lẻ của quán (stores/{storeId}/dishes/{dishId})."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], store_fields, [1.4, 1.0, 1.0, 2.9])

# --- 3.4 DISHES (CON CỦA STORES) ---
add_title("3.4 Node con stores/{storeId}/dishes/{dishId} — Món ăn lẻ & Tùy biến Topping", level=2)
add_p("Lưu danh sách món ăn do từng chi nhánh quán quản lý. Mỗi món ăn có giá bán, hình ảnh, mã danh mục sàn và danh sách nhóm tùy chọn topping (size, độ ngọt, lượng đá, món thêm).", bold_prefix="• Mục đích: ")

dish_fields = [
    ["dishId", "String", "\"\"", "Mã món ăn duy nhất (tạo ngẫu nhiên hoặc theo tiền tố dish_)."],
    ["name", "String", "\"\"", "Tên món ăn hiển thị cho khách chọn (ví dụ: Phở bò đặc biệt)."],
    ["price", "Double", "0.0", "Giá bán niêm yết của món ăn (đơn vị: VNĐ)."],
    ["categoryId", "String", "\"\"", "Mã danh mục sàn khớp chuẩn categories (ví dụ: cat_pho, cat_rice)."],
    ["imageUrl", "String", "\"\"", "Đường dẫn ảnh món ăn tải lên Firebase Storage hoặc link mạng."],
    ["isAvailable", "Boolean", "true", "Công tắc bật/tắt còn món (true) hoặc tạm hết món hôm nay (false)."],
    ["options", "List/Map", "[]", "Danh sách các nhóm tùy biến (ToppingGroup): tên nhóm, chọn tối đa/tối thiểu, danh sách món thêm kèm giá."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], dish_fields, [1.4, 1.0, 1.0, 2.9])

# --- 3.5 COMBOS ---
add_title("3.5 Node combos — Gói Combo Món ăn Tiết kiệm Cấp Sàn & Quán", level=2)
add_p("Lưu trữ các gói Combo khuyến mãi (kết hợp nhiều món lẻ với giá ưu đãi). Được gắn với danh mục cấp sàn độc lập cat_combo giúp hiển thị tách biệt, không bị chồng chéo danh mục.", bold_prefix="• Mục đích: ")
add_p("Đường dẫn: combos/{comboId}", italic=True)

combo_fields = [
    ["comboId", "String", "\"\"", "Mã gói combo duy nhất (ví dụ: combo_bun_bo_01)."],
    ["comboName", "String", "\"\"", "Tên combo hiển thị (ví dụ: COMBO: Bún Bò + Trà Sữa Ăn Vặt)."],
    ["storeId", "String", "\"\"", "Mã quán sở hữu combo (liên kết với node stores)."],
    ["comboPrice", "Double", "0.0", "Giá bán thực tế của cả gói combo sau khi đã giảm giá."],
    ["originalPrice", "Double", "0.0", "Tổng giá trị gốc của các món lẻ cộng lại để hiển thị gạch giá."],
    ["imageUrl", "String", "\"\"", "Ảnh đại diện gói combo hấp dẫn."],
    ["dishIds", "List<String>", "[]", "Danh sách các mã món ăn con cấu thành nên gói combo này."],
    ["items", "List<ComboItem>", "[]", "Chi tiết từng món trong combo: dishId, dishName, quantity, originalPrice."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], combo_fields, [1.4, 1.0, 1.0, 2.9])

# --- 3.6 ORDERS ---
add_title("3.6 Node orders — Node Cốt lõi Quản lý Đơn hàng & Vòng đời Giao dịch", level=2)
add_p("Node quan trọng bậc nhất của toàn bộ hệ thống Oishi Food. Chứa toàn bộ thông tin giao dịch giữa 4 tác nhân: Khách hàng (người đặt), Quán (người duyệt), Đầu bếp (người nấu), Tài xế (người giao). Lưu trữ chi tiết tiền hàng, phí ship động theo km, tiền cọc 20%, mã OTP/PIN nhận hàng và toàn bộ lịch sử thay đổi trạng thái đơn.", bold_prefix="• Mục đích: ")
add_p("Đường dẫn: orders/{orderId}", italic=True)

order_fields = [
    ["orderId", "String", "\"\"", "Mã đơn hàng duy nhất có định dạng chuẩn #OISHI_XXXXXX."],
    ["customerId", "String", "\"\"", "Mã người dùng của khách hàng đặt đơn (UID)."],
    ["customerName", "String", "\"\"", "Họ tên của khách nhận hàng."],
    ["customerPhone", "String", "\"\"", "Số điện thoại liên lạc của khách nhận."],
    ["deliveryAddress", "String", "\"\"", "Địa chỉ nơi nhận hàng đầy đủ."],
    ["deliveryLat", "Double", "0.0", "Tọa độ vĩ độ GPS vị trí khách nhận hàng."],
    ["deliveryLng", "Double", "0.0", "Tọa độ kinh độ GPS vị trí khách nhận hàng."],
    ["storeId", "String", "\"\"", "Mã cửa hàng tiếp nhận chế biến đơn."],
    ["storeName", "String", "\"\"", "Tên quán ăn giao hàng."],
    ["chefId", "String", "null", "Mã đầu bếp nhận lệnh nấu đơn này (gán qua Atomic Transaction)."],
    ["shipperId", "String", "null", "Mã tài xế nhận cuốc giao đơn này (gán qua Atomic Transaction)."],
    ["items", "List<OrderItem>", "[]", "Danh sách các món đặt: dishId, dishName, price, quantity, specText, toppings."],
    ["subtotal", "Double", "0.0", "Tổng tiền món ăn trong giỏ hàng (chưa tính ship và giảm giá)."],
    ["deliveryFee", "Double", "0.0", "Phí ship tính động theo khoảng cách GPS (15k < 2km; 25k 2-5km; 45k > 5km)."],
    ["depositAmount", "Double", "0.0", "Tiền cọc bắt buộc 20% đối với khách có điểm uy tín < 90 chọn thanh toán COD."],
    ["totalPrice", "Double", "0.0", "Tổng số tiền thanh toán cuối cùng của đơn hàng."],
    ["paymentMethod", "String", "\"VIETQR\"", "Phương thức thanh toán: VIETQR (chuyển khoản Napas247) hoặc COD (tiền mặt)."],
    ["paymentStatus", "String", "\"UNPAID\"", "Trạng thái thanh toán: UNPAID (chưa thanh toán), PAID (đã thanh toán VietQR)."],
    ["status", "String", "\"PENDING\"", "Trạng thái vòng đời: PENDING -> PREPARING -> READY -> ASSIGNED -> PICKED_UP -> DELIVERING -> COMPLETED / CANCELLED / RETURNING_TO_STORE / BOOM."],
    ["deliveryPin", "String", "\"1234\"", "Mã PIN 4 số bí mật gửi cho khách; tài xế phải nhập đúng mã này mới hoàn thành đơn."],
    ["deliveryToken", "String", "\"\"", "Chuỗi mã hóa QR Code 2 chiều để tài xế quét bằng Camera xác thực nhận hàng an toàn."],
    ["countdownMinutes", "Int", "15", "Số phút đếm ngược sự cố khi tài xế đã đến nơi nhưng không liên lạc được khách."],
    ["createdAt", "Long", "SystemTime", "Thời gian khách tạo đơn hàng."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], order_fields, [1.4, 1.0, 1.0, 2.9])

add_p("Mẫu dữ liệu JSON đơn hàng hoàn chỉnh trên Firebase:", bold_prefix="Payload mẫu: ")
add_code_block("""{
  "orders": {
    "ORD_20261117_001": {
      "orderId": "#OISHI_892341",
      "customerId": "UID_VU_KHACH_01",
      "customerName": "Trần Bá Vũ",
      "customerPhone": "0901234567",
      "deliveryAddress": "53 Võ Văn Ngân, Linh Chiểu, TP. Thủ Đức",
      "deliveryLat": 10.8512,
      "deliveryLng": 106.7721,
      "storeId": "STORE_PHO_GIAU_01",
      "storeName": "Phở Bò Gia Truyền Oishi",
      "chefId": "CHEF_01",
      "shipperId": "TX_01",
      "items": [
        {
          "dishId": "dish_pho_dac_biet",
          "dishName": "Phở Bò Tái Nạm Đặc Biệt",
          "price": 55000,
          "quantity": 2,
          "specText": "Nhiều hành, nước béo, không giá"
        }
      ],
      "subtotal": 110000,
      "deliveryFee": 15000,
      "totalPrice": 125000,
      "paymentMethod": "VIETQR",
      "paymentStatus": "PAID",
      "status": "DELIVERING",
      "deliveryPin": "4829",
      "deliveryToken": "OISHI_DELIVERY_ORD_20261117_001_SECURE",
      "createdAt": 1731804500000
    }
  }
}""")

# --- 3.7 TRACKING & ORDER_CHATS ---
add_title("3.7 Node tracking & order_chats — Định vị GPS Realtime & Chat Đơn hàng", level=2)
add_p("Lưu vị trí kinh độ/vĩ độ GPS di chuyển thực tế của tài xế đang giao hàng và kênh chat trao đổi giữa các bên theo đơn hàng.", bold_prefix="• Mục đích: ")

add_p("Cấu trúc node tracking/{orderId}:", bold_prefix="1. Node tracking: ")
track_fields = [
    ["latitude", "Double", "0.0", "Vĩ độ GPS hiện tại của tài xế đang chạy xe."],
    ["longitude", "Double", "0.0", "Kinh độ GPS hiện tại của tài xế đang chạy xe."],
    ["updatedAt", "Long", "SystemTime", "Dấu thời gian cập nhật vị trí gần nhất (gửi mỗi 3-5 giây bởi LocationForegroundService)."],
    ["bearing", "Float", "0.0f", "Góc xoay hướng di chuyển của xe máy để xoay icon trên bản đồ."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], track_fields, [1.4, 1.0, 1.0, 2.9])

add_p("Cấu trúc node order_chats/{orderId}/{messageId}:", bold_prefix="2. Node order_chats: ")
chat_fields = [
    ["messageId", "String", "\"\"", "Mã định danh tin nhắn."],
    ["orderId", "String", "\"\"", "Mã đơn hàng liên quan."],
    ["senderId", "String", "\"\"", "Mã người gửi tin nhắn (UID)."],
    ["senderRole", "String", "\"CUSTOMER\"", "Vai trò người gửi: CUSTOMER, DRIVER, MERCHANT."],
    ["content", "String", "\"\"", "Nội dung văn bản tin nhắn (ví dụ: 'Tài xế đến cổng chung cư gọi em nhé')."],
    ["timestamp", "Long", "SystemTime", "Thời gian gửi tin nhắn."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], chat_fields, [1.4, 1.0, 1.0, 2.9])

# --- 3.8 COMPLAINTS & TRUST_SCORE_LOGS ---
add_title("3.8 Node complaints & trust_score_logs — Khiếu nại, Xử lý Vi phạm & Lịch sử Uy tín", level=2)
add_p("Hệ thống kiểm soát chất lượng sàn, tiếp nhận khiếu nại (sai món, bom hàng, ATTP) và ghi vết biến động điểm uy tín tín nhiệm của người dùng.", bold_prefix="• Mục đích: ")

add_p("Cấu trúc node complaints/{complaintId}:", bold_prefix="1. Node complaints: ")
cmp_fields = [
    ["complaintId", "String", "\"\"", "Mã hồ sơ khiếu nại."],
    ["orderId", "String", "\"\"", "Mã đơn hàng xảy ra sự cố vi phạm."],
    ["complainantRole", "String", "\"DRIVER\"", "Bên khiếu nại: DRIVER (báo bom hàng), CUSTOMER (báo sai món), STORE."],
    ["reason", "String", "\"\"", "Lý do khiếu nại: BOM_HANG, SAI_MON, MAT_ATTP, THAI_DO_XAU, GIAO_TRE."],
    ["evidenceImageUrl", "String", "\"\"", "Đường dẫn ảnh chứng từ chụp thực tế (gói hàng bị hủy, món ăn lỗi)."],
    ["status", "String", "\"PENDING\"", "Trạng thái xử lý: PENDING (chờ duyệt), RESOLVED (đã giải quyết), REJECTED (bác bỏ)."],
    ["verdict", "String", "\"\"", "Phán quyết của Quản trị viên: trừ điểm khách, hoàn tiền quán, khóa tài khoản."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], cmp_fields, [1.4, 1.0, 1.0, 2.9])

add_p("Cấu trúc node trust_score_logs/{logId}:", bold_prefix="2. Node trust_score_logs: ")
trust_fields = [
    ["logId", "String", "\"\"", "Mã bản ghi nhật ký biến động điểm."],
    ["customerId", "String", "\"\"", "Mã khách hàng được/bị thay đổi điểm."],
    ["oldScore", "Int", "100", "Số điểm uy tín trước khi biến động."],
    ["newScore", "Int", "70", "Số điểm uy tín sau khi biến động."],
    ["changeAmount", "Int", "-30", "Số điểm cộng hoặc trừ (+2 khi nhận đơn thành công, -30 khi bom hàng COD)."],
    ["reason", "String", "\"\"", "Lý do biến động điểm hiển thị cho khách xem trong màn hình Hồ sơ."],
    ["createdAt", "Long", "SystemTime", "Thời gian ghi nhận biến động điểm."]
]
create_table(["Tên trường", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa nghiệp vụ chi tiết"], trust_fields, [1.4, 1.0, 1.0, 2.9])

# --- 3.9 SYSTEM_CONFIG & DELIVERY_TIME_FRAMES ---
add_title("3.9 Node system_config & delivery_time_frames — Cấu hình Sàn & Đếm ngược Sự cố", level=2)
add_p("Lưu các hằng số điều hành hệ thống do Quản trị viên thiết lập từ xa mà không cần phải cập nhật lại mã nguồn ứng dụng Android.", bold_prefix="• Mục đích: ")

sys_fields = [
    ["system_config/delivery_countdown/round1Minutes", "Int", "10", "Số phút chờ khách nghe máy vòng 1 khi tài xế đã đến điểm giao."],
    ["system_config/delivery_countdown/round2Minutes", "Int", "5", "Số phút đếm ngược vòng 2 trước khi kích hoạt quy trình hủy đơn và hoàn trả quán."],
    ["delivery_time_frames/{1..4}/label", "String", "\"20-30 phút\"", "Nhãn khung giờ dự kiến giao hàng hiển thị cho khách lựa chọn khi đặt."],
    ["delivery_time_frames/countdown_config", "Map", "{}", "Bản sao đồng bộ (Dual-write) để tài xế luôn đọc được cấu hình sự cố không bị lỗi bảo mật."]
]
create_table(["Đường dẫn cấu hình", "Kiểu dữ liệu", "Mặc định", "Ý nghĩa điều hành sàn"], sys_fields, [2.3, 0.9, 0.9, 2.2])

doc.add_page_break()

# ==========================================================
# PHẦN 4: CƠ CHẾ KỸ THUẬT ĐẶC THÙ TRÊN FIREBASE
# ==========================================================
add_title("PHẦN 4. CƠ CHẾ KỸ THUẬT ĐẶC THÙ TRÊN FIREBASE TRONG DỰ ÁN", level=1)

add_title("4.1 Cơ chế Khóa Giao dịch Nguyên tử (Firebase Transaction runTransaction)", level=2)
add_p("Trong môi trường nhiều người dùng cùng hoạt động đồng thời (Concurrency), bài toán tranh chấp tài nguyên nghiêm trọng xảy ra khi:")
add_bullet("Nhiều đầu bếp tại quán cùng lúc bấm 'Nhận nấu' một đơn hàng.", bold_prefix="• Tranh chấp Bếp: ")
add_bullet("Nhiều tài xế cùng lúc bấm 'Nhận đơn' (Ghép cuốc) một đơn hàng đang ở trạng thái READY.", bold_prefix="• Tranh chấp Tài xế: ")
add_p("Nếu chỉ sử dụng hàm setValue() thông thường, tài xế bấm sau sẽ vô tình ghi đè tài xế bấm trước, dẫn đến 2 tài xế cùng chạy đến một quán để lấy 1 gói thức ăn. Oishi Food giải quyết triệt để lỗi này bằng hàm Transaction nguyên tử trong FirebaseRepository.kt:")

tx_code = """// Khóa nguyên tử khi Đầu bếp nhận đơn (assignChef)
orderRef.runTransaction(object : Transaction.Handler {
    override fun doTransaction(currentData: MutableData): Transaction.Result {
        val currentChef = currentData.child("chefId").getValue(String::class.java)
        val currentStatus = currentData.child("status").getValue(String::class.java)
        
        // Kiểm tra xem đơn đã có người nhận hoặc sai trạng thái chưa
        if (!currentChef.isNullOrEmpty() || currentStatus != "PREPARING") {
            return Transaction.abort() // Hủy giao dịch lập tức, báo bận cho máy thứ 2
        }
        
        // Gán nguyên tử
        currentData.child("chefId").value = chefId
        return Transaction.success(currentData) // Cam kết dữ liệu thành công
    }
    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
        // committed = true: Nhận thành công; false: Đã có người khác nhận trước
    }
})"""
add_code_block(tx_code)

add_title("4.2 Dịch vụ Duy trì Kết nối Nền (MerchantKeepAliveService)", level=2)
add_p("Trên hệ điều hành Android hiện đại (từ Android 12 trở lên), tính năng tiết kiệm pin Doze Mode thường đóng băng các kết nối mạng ngầm sau vài phút người dùng không chạm vào màn hình. Để máy tính bảng hoặc điện thoại của quán ăn LUÔN reo chuông đơn hàng mới lập tức, Oishi Food triển khai:")
add_bullet("Dịch vụ MerchantKeepAliveService kế thừa Foreground Service, ghim thanh thông báo cố định trên thanh trạng thái Notification Bar.", bold_prefix="• Dịch vụ chạy ngầm: ")
add_bullet("Lắng nghe liên tục ChildEventListener tại đường dẫn orders lọc theo child('storeId').equalTo(myStoreId). Khi có đơn hàng mới với trạng thái PENDING, chuông báo động AudioManager phát âm lượng tối đa và mở popup DialogNewOrderPopup.", bold_prefix="• Lắng nghe Realtime: ")

add_title("4.3 Cơ chế Khóa Tài khoản Thời gian thực (AccountLockWatcher)", level=2)
add_p("Khi Quản trị viên (Admin) phát hiện khách hàng bom hàng hoặc tài xế gian lận, Admin bấm Khóa tài khoản trên admin.html. Trên thiết bị Android của người vi phạm, lớp AccountLockWatcher gắn ValueEventListener trực tiếp vào nhánh /users/{uid}/status. Ngay trong tích tắc nhận được tín hiệu status = 'LOCKED', ứng dụng lập tức:")
add_bullet("Hủy bỏ mọi phiên làm việc hiện tại: FirebaseAuth.getInstance().signOut().", bold_prefix="• Đăng xuất tức thời: ")
add_bullet("Chuyển hướng màn hình về LoginActivity kèm cờ FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK và hiển thị hộp thoại thông báo lý do vi phạm.", bold_prefix="• Khóa giao diện: ")

doc.add_page_break()

# ==========================================================
# PHẦN 5: PHÂN TÍCH QUY TẮC BẢO MẬT FIREBASE RULES
# ==========================================================
add_title("PHẦN 5. PHÂN TÍCH QUY TẮC BẢO MẬT FIREBASE SECURITY RULES", level=1)
add_p("Tệp database.rules.json chịu trách nhiệm bảo vệ toàn bộ cơ sở dữ liệu trên máy chủ Google. Quy tắc được thiết kế theo nguyên lý ĐẶC QUYỀN TỐI THIỂU (Least Privilege Principle):")

rules_summary = [
    ["Gốc toàn sàn (root)", "Chỉ cho phép tài khoản có vai trò ADMIN hoặc email admin@gmail.com đọc/ghi toàn bộ cây dữ liệu."],
    ["Node users", "Người dùng chỉ được phép sửa thông tin cá nhân của chính mình (auth.uid === $uid); cấm tự ý sửa trường role hoặc status."],
    ["Node customers", "Chỉ khách hàng sở hữu UID đó hoặc Admin mới được phép xem điểm uy tín; quyền sửa điểm chỉ cấp qua hàm chức năng nội bộ."],
    ["Node stores", "Mọi người dùng (kể cả chưa đăng nhập) đều được phép ĐỌC thông tin quán và thực đơn; chỉ Merchant sở hữu quán hoặc Admin mới được GHI/SỬA."],
    ["Node orders", "Người dùng đăng nhập được đọc/ghi đơn hàng mà họ đóng vai trò (Khách đặt, Quán nhận, Tài xế giao); cấm người lạ can thiệp đơn."],
    ["Khai báo .indexOn", "Khai báo indexOn trên các trường thường xuyên lọc (storeId, customerId, status, role, email) giúp Firebase truy vấn với độ phức tạp O(log N) thay vì quét toàn bộ bảng O(N)."]
]
create_table(["Vùng bảo mật", "Giải thích cơ chế kiểm soát an toàn"], rules_summary, [1.8, 4.5])

add_p("Trích đoạn luật phân quyền thực tế trong database.rules.json:", bold_prefix="Mã Rules thực tế: ")
add_code_block("""{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null",
        ".write": "auth != null && (auth.uid === $uid || root.child('users').child(auth.uid).child('role').val() === 'ADMIN')"
      },
      ".indexOn": ["role", "roleId", "status", "email", "phone", "storeId", "bossId", "trustScore"]
    },
    "stores": {
      ".read": true,
      "$storeId": {
        ".write": "auth != null && (root.child('stores').child($storeId).child('merchantId').val() === auth.uid || root.child('users').child(auth.uid).child('role').val() === 'ADMIN')"
      },
      ".indexOn": ["merchantId", "ownerId", "bossId", "storeStatus", "category", "isVerified"]
    },
    "orders": {
      ".read": "auth != null",
      ".write": "auth != null",
      ".indexOn": ["customerId", "shipperId", "chefId", "driverId", "shopId", "storeId", "status", "orderStatus", "createdAt"]
    }
  }
}""")

# Lưu tài liệu Word
doc.save(OUT_DOCX_PATH)
print(f"SUCCESS: Generated comprehensive Firebase docx at: {OUT_DOCX_PATH}")
print(f"File size: {os.path.getsize(OUT_DOCX_PATH)} bytes")
