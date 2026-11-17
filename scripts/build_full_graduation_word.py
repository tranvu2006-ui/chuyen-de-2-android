import os
import sys

# Thêm thư mục scripts vào đường dẫn tìm kiếm module
script_dir = os.path.dirname(os.path.abspath(__file__))
if script_dir not in sys.path:
    sys.path.append(script_dir)

import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL

from builder_base import (
    doc, out_word_path, diag_dir, sc_dir, tc_dir,
    add_title, add_p, add_bullet, add_image_box, create_table, add_table_of_contents
)

print("Starting to build FULL Graduation Report Word document with Headings & TOC...")

# ==========================================================
# TRANG BÌA CHÍNH (COVER PAGE)
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

# Logo đề tài Oishi Food trang bìa
p_logo = doc.add_paragraph()
p_logo.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_logo.paragraph_format.space_before = Pt(12)
p_logo.paragraph_format.space_after = Pt(14)
logo_path = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\logo-app-khach.png"
if os.path.exists(logo_path):
    run_logo = p_logo.add_run()
    run_logo.add_picture(logo_path, width=Inches(1.8))

p_line = doc.add_paragraph()
p_line.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_line.paragraph_format.space_after = Pt(20)
r_line = p_line.add_run("-------------------***-------------------")
r_line.font.name = 'Times New Roman'
r_line.bold = True

p_type = doc.add_paragraph()
p_type.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_type.paragraph_format.space_after = Pt(12)
r_type = p_type.add_run("BÁO CÁO KẾT THÚC MÔN HỌC\nCHUYÊN ĐỀ TỐT NGHIỆP 2 - LẬP TRÌNH DI ĐỘNG 2")
r_type.font.name = 'Times New Roman'
r_type.font.size = Pt(15)
r_type.bold = True
r_type.font.color.rgb = RGBColor(0x1E, 0x29, 0x3B)

p_subject = doc.add_paragraph()
p_subject.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_subject.paragraph_format.space_before = Pt(18)
p_subject.paragraph_format.space_after = Pt(36)
r_subj_lbl = p_subject.add_run("ĐỀ TÀI:\n")
r_subj_lbl.font.size = Pt(14)
r_subj_lbl.bold = True
r_subj = p_subject.add_run("HỆ THỐNG ĐẶT VÀ GIAO ĐỒ ĂN TRỰC TUYẾN\nĐA PHÂN HỆ OISHI FOOD")
r_subj.font.size = Pt(18)
r_subj.bold = True
r_subj.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A)

# Bảng thông tin sinh viên & giảng viên
info_rows = [
    ["Chuyên ngành:", "Công nghệ Thông tin / Lập trình Thiết bị Di động"],
    ["Lớp học phần:", "CD24TT7"],
    ["Giảng viên hướng dẫn:", "ThS. TIÊU KIM CƯƠNG"],
    ["Sinh viên thực hiện 1:", "PHẠM PHÚC MẠNH GIÀU (MSSV: 24211TT3571) - Trưởng nhóm"],
    ["Sinh viên thực hiện 2:", "TRẦN VŨ (MSSV: 24211TT2515) - Thành viên"],
    ["Sinh viên thực hiện 3:", "DƯƠNG MINH THƯ (MSSV: 24211TTxxxx) - Thành viên"],
    ["Sinh viên thực hiện 4:", "HỒ THỊ VÂN ANH (MSSV: 24211TTxxxx) - Thành viên"],
]
info_tbl = doc.add_table(rows=len(info_rows), cols=2)
info_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
for idx, (label, val) in enumerate(info_rows):
    cell_lbl, cell_val = info_tbl.rows[idx].cells
    cell_lbl.width = Inches(2.2)
    cell_val.width = Inches(4.2)
    
    p1 = cell_lbl.paragraphs[0]
    p1.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r1 = p1.add_run(label)
    r1.font.name = 'Times New Roman'
    r1.font.size = Pt(12)
    r1.bold = True
    
    p2 = cell_val.paragraphs[0]
    p2.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r2 = p2.add_run(val)
    r2.font.name = 'Times New Roman'
    r2.font.size = Pt(12)
    if "GIÀU" in val or "VŨ" in val or "THƯ" in val or "CƯƠNG" in val:
        r2.bold = True

p_bot = doc.add_paragraph()
p_bot.alignment = WD_ALIGN_PARAGRAPH.CENTER
p_bot.paragraph_format.space_before = Pt(48)
r_bot = p_bot.add_run("TP. HỒ CHÍ MINH, NĂM 2026")
r_bot.font.name = 'Times New Roman'
r_bot.font.size = Pt(12)
r_bot.bold = True

doc.add_page_break()

# ==========================================================
# BẢNG NHẬT KÝ VÀ PHÂN CÔNG ĐÁNH GIÁ (33% - 33% - 33% - 1%)
# ==========================================================
add_title("PHÂN CÔNG BÁO CÁO", level=1, space_before=18)
add_p("Căn cứ theo quá trình triển khai thực tế mã nguồn, thiết kế kiến trúc và mức độ hoàn thành nhiệm vụ trong suốt học kỳ, nhóm thống nhất đánh giá tỷ lệ đóng góp của từng thành viên như sau:")

diary_headers = ["STT", "Họ và tên", "Nhiệm vụ kỹ thuật phụ trách chính", "Đánh giá (%)", "Chữ ký"]
diary_rows = [
    [
        "1",
        "Phạm Phúc Mạnh Giàu\n(24211TT3571)\nTrưởng nhóm",
        "• Phát triển ứng dụng Khách hàng app-customer (HomeActivity, MenuActivity, CartActivity, CheckoutActivity, PaymentActivity).\n• Xây dựng màn hình Theo dõi đơn hàng realtime (TrackingActivity) & dẫn đường Maps Polyline.\n• Thiết kế luồng hủy đơn khi đang giao & cơ chế Điểm uy tín (TrustScoreLog, phạt 20 điểm bùng đơn).\n• Phát triển hệ thống giải quyết khiếu nại (ComplaintActivity, IncidentProof).",
        "33%",
        ""
    ],
    [
        "2",
        "Trần Vũ\n(24211TT2515)\nThành viên",
        "• Phát triển phân hệ Quán ăn & Chủ chuỗi trong app-internal (OrderListFragment Kanban 4 tab, MenuManageFragment, BossDashboardActivity).\n• Xây dựng phân hệ Bếp ChefFragment (chế biến, atomic lock nhận nấu, đổi trạng thái READY).\n• Xây dựng phân hệ Tài xế (ShipperFragment, DeliveryTripActivity, ghép đơn đa điểm, quét camera QR/PIN 4 số, xử lý sự cố 15 phút RETURNING_TO_STORE).\n• Hiện thực dịch vụ ngầm BankNotificationListenerService (tự động duyệt VietQR Napas247).",
        "33%",
        ""
    ],
    [
        "3",
        "Dương Minh Thư\n(24211TTxxxx)\nThành viên",
        "• Thiết kế kiến trúc Firebase Realtime Database phân cấp (15 node) & Firebase Authentication (Email OTP, công tắc Bypass OTP).\n• Xây dựng module lõi core (26 Data Models, FirebaseRepository 178 hàm, AuthRepository 20 hàm, GeolocationUtils, NotificationHelper, StatusUtils).\n• Phát triển ứng dụng Android Quản trị viên app-admin hoàn chỉnh (5 tab: Dashboard KPI, Quản lý Cửa hàng, Danh mục món toàn sàn, Xử lý Khiếu nại & Phán quyết, Giám sát Realtime).\n• Tổng hợp và biên tập tài liệu báo cáo kỹ thuật chuẩn TDC.",
        "33%",
        ""
    ],
    [
        "4",
        "Hồ Thị Vân Anh\n(24211TTxxxx)\nThành viên",
        "• Hỗ trợ thu thập tài liệu nghiệp vụ giao nhận đồ ăn.\n• Tham gia kiểm thử giao diện sơ bộ (UI Test) trên thiết bị di động.\n• Hỗ trợ chuẩn bị nội dung slide thuyết trình bảo vệ đồ án.",
        "1%",
        ""
    ],
]
create_table(diary_headers, diary_rows, col_widths=[0.5, 1.6, 3.2, 0.8, 0.7])

doc.add_page_break()

# ==========================================================
# TRANG MỤC LỤC TỰ ĐỘNG (TABLE OF CONTENTS)
# ==========================================================
add_title("MỤC LỤC", level=1, space_before=18)
add_p("Nhấp chuột phải vào vùng mục lục bên dưới và chọn 'Update Field' (hoặc nhấn phím F9) để Word tự động làm mới số trang chính xác nhất:", italic=True)
add_table_of_contents()

doc.add_page_break()

# ==========================================================
# DANH MỤC TỪ VIẾT TẮT, HÌNH VẼ & BẢNG BIỂU
# ==========================================================
add_title("DANH MỤC CÁC TỪ VIẾT TẮT", level=1, space_before=18)
abbr_headers = ["Từ viết tắt", "Cụm từ tiếng Anh đầy đủ", "Ý nghĩa / Giải thích trong hệ thống Oishi Food"]
abbr_rows = [
    ["RBAC", "Role-Based Access Control", "Kiểm soát truy cập dựa trên 6 vai trò người dùng"],
    ["F&B", "Food and Beverage", "Ngành dịch vụ ăn uống và nhà hàng trực tuyến"],
    ["RTDB", "Realtime Database", "Cơ sở dữ liệu thời gian thực của nền tảng Google Firebase"],
    ["OTP", "One-Time Password", "Mã mật khẩu dùng một lần xác thực qua Email"],
    ["VietQR", "Vietnam Quick Response", "Chuẩn mã QR thanh toán liên ngân hàng Napas247"],
    ["GPS", "Global Positioning System", "Hệ thống định vị toàn cầu theo dõi vị trí tài xế"],
    ["COD", "Cash On Delivery", "Thanh toán bằng tiền mặt khi nhận hàng"],
    ["SRS", "Software Requirements Specification", "Bản đặc tả yêu cầu phần mềm"],
    ["SDS", "Software Design Specification", "Bản đặc tả thiết kế phần mềm chi tiết"],
    ["TSP", "Traveling Salesperson Problem", "Bài toán người giao hàng tối ưu lộ trình ghép đơn"],
    ["DI", "Dependency Injection", "Kỹ thuật tiêm phụ thuộc mã nguồn với Hilt / Dagger"]
]
create_table(abbr_headers, abbr_rows, col_widths=[1.2, 2.5, 3.1])

add_title("DANH MỤC HÌNH VẼ VÀ SƠ ĐỒ", level=1, space_before=18)
fig_headers = ["Ký hiệu hình", "Tên sơ đồ / Hình ảnh giao diện minh họa", "Vị trí mục"]
fig_rows = [
    ["Sơ đồ 1", "Sơ đồ Use Case tổng quát hệ thống Oishi Food (6 Roles RBAC)", "Mục 2.2"],
    ["Sơ đồ 2", "Sơ đồ mối liên hệ luồng vận hành giữa 6 vai trò trong hệ thống", "Mục 2.3"],
    ["Sơ đồ 3", "Sơ đồ lớp (Class Diagram) toàn diện hệ thống", "Mục 2.5.1"],
    ["Sơ đồ 4", "Sơ đồ tuần tự: Đặt hàng – Duyệt VietQR – Bếp nấu – Giao QR/PIN", "Mục 2.5.2"],
    ["Sơ đồ 5", "Sơ đồ tuần tự: Khách hủy đơn khi đang giao & Hoàn trả Bếp", "Mục 2.5.3"],
    ["Sơ đồ 6", "Sơ đồ máy trạng thái vòng đời đơn hàng (Order State Machine)", "Mục 2.5.4"],
    ["Ảnh 1 – Ảnh 10", "Bộ ảnh chụp giao diện Ứng dụng Khách hàng (app-customer)", "Mục 2.6 - Phần A"],
    ["Ảnh 11 – Ảnh 21", "Bộ ảnh chụp giao diện Ứng dụng Nội bộ Quán, Bếp & Tài xế (app-internal)", "Mục 2.6 - Phần B"],
    ["Ảnh 22 – Ảnh 24", "Bộ ảnh chụp giao diện Ứng dụng Android Quản trị viên (app-admin Native)", "Mục 2.6 - Phần C"],
    ["Ảnh KT-1 – KT-6", "Bộ ảnh chụp minh chứng kết quả kiểm thử thực nghiệm hệ thống", "Mục 3.2"]
]
create_table(fig_headers, fig_rows, col_widths=[1.5, 4.3, 1.0])

add_title("DANH MỤC BẢNG BIỂU", level=1, space_before=18)
tbl_headers = ["Ký hiệu bảng", "Tên bảng thống kê / đặc tả kỹ thuật", "Vị trí mục"]
tbl_rows = [
    ["Bảng 1", "Bảng phân công nhiệm vụ và đánh giá đóng góp thành viên", "Đầu tài liệu"],
    ["Bảng 2", "Ma trận phân quyền 6 Roles (RBAC Matrix) trong hệ thống", "Mục 2.1"],
    ["Bảng 3", "Đặc tả các nhánh dữ liệu Firebase Realtime Database", "Mục 2.4"],
    ["Bảng 4 – Bảng 27", "Bảng yêu cầu chức năng và phản hồi 24 màn hình giao diện (IEEE)", "Mục 2.6"],
    ["Bảng 28 – Bảng 33", "Bảng kịch bản kiểm thử thực nghiệm chi tiết (KT-1 đến KT-6)", "Mục 3.2"]
]
create_table(tbl_headers, tbl_rows, col_widths=[1.5, 4.3, 1.0])

doc.add_page_break()

# ==========================================================
# LỜI MỞ ĐẦU
# ==========================================================
add_title("LỜI MỞ ĐẦU", level=1, space_before=18)
add_p("Trong kỷ nguyên chuyển đổi số và sự bùng nổ mạnh mẽ của kinh tế số, thói quen tiêu dùng của người dân tại các đô thị lớn đã thay đổi sâu sắc. Nhu cầu đặt đồ ăn trực tuyến (Food Delivery) giao tận nơi đã trở thành một phần không thể thiếu trong đời sống hàng ngày của người tiêu dùng hiện đại. Sự tiện lợi trong việc lựa chọn đa dạng món ăn, tiết kiệm thời gian di chuyển và thanh toán không dùng tiền mặt đã tạo nên thị trường giao đồ ăn trực tuyến vô cùng sôi động.")
add_p("Tuy nhiên, bên cạnh những bước phát triển vượt bậc, thị trường giao đồ ăn hiện tại cũng bộc lộ những vấn đề nhức nhối đối với các bên tham gia: tình trạng khách hàng 'bùng đơn' (bom hàng) gây thất thoát nặng nề cho nhà hàng và tài xế; quy trình thanh toán chuyển khoản ngân hàng thủ công buộc chủ quán phải tự chụp màn hình hoặc kiểm tra sao kê đối soát chậm chạp; sự thiếu liên kết chặt chẽ giữa khu vực chế biến (Bếp) và đội ngũ giao hàng dẫn đến thức ăn bị nguội hoặc đơn hàng bị dồn ứ.")
add_p("Xuất phát từ thực tiễn trên, nhóm chúng em đã quyết định lựa chọn và triển khai đề tài:", bold_prefix="Mục đích nghiên cứu: ")
add_p("'HỆ THỐNG ĐẶT VÀ GIAO ĐỒ ĂN TRỰC TUYẾN ĐA PHÂN HỆ OISHI FOOD'", bold_prefix="", italic=True, align=WD_ALIGN_PARAGRAPH.CENTER)
add_p("Đồ án hướng tới việc ứng dụng những công nghệ di động và điện toán đám mây tiên tiến nhất hiện nay (Android Multi-Module Clean Architecture, Kotlin Coroutines, Jetpack Hilt, Firebase Realtime Database thời gian thực, Google Maps SDK, chuẩn thanh toán Napas247 VietQR) để xây dựng một giải pháp hoàn chỉnh, minh bạch, an toàn và tối ưu cho cả 6 vai trò: Khách hàng, Chủ chuỗi, Quản lý chi nhánh, Đầu bếp, Tài xế và Quản trị viên sàn.")

doc.add_page_break()

# ==========================================================
# CHƯƠNG 1. KHẢO SÁT HIỆN TRẠNG VÀ PHÁT BIỂU BÀI TOÁN
# ==========================================================
add_title("CHƯƠNG 1. KHẢO SÁT HIỆN TRẠNG VÀ PHÁT BIỂU BÀI TOÁN", level=1)

add_title("1.1 Khảo sát hiện trạng thị trường giao nhận đồ ăn trực tuyến", level=2)
add_p("Thị trường giao thức ăn trực tuyến tại Việt Nam chứng kiến sự cạnh tranh khốc liệt giữa các ứng dụng lớn như ShopeeFood, GrabFood, Baemin (trước đây) và GoFood. Mặc dù các nền tảng này đã giải quyết tốt bài toán kết nối cơ bản, việc khảo sát thực tế tại các hàng quán cho thấy vẫn còn tồn tại những bất cập lớn:")
add_bullet("Vấn nạn bom hàng, hủy đơn vô tội vạ: Người mua đặt đơn với phương thức COD nhưng không nghe máy hoặc hủy ngang khi tài xế đã di chuyển tới nơi. Nhà hàng phải chịu mất mát chi phí nguyên vật liệu, trong khi tài xế mất công sức và xăng xe mà không có cơ chế bồi hoàn thích đáng.", bold_prefix="1. Thất thoát do bùng đơn: ")
add_bullet("Quy trình thanh toán chuyển khoản thủ công: Nhiều khách hàng thanh toán qua ngân hàng nhưng quán ăn phải chờ chụp ảnh biên lai, dễ bị làm giả ảnh chụp màn hình giao dịch (fake bill chuyển khoản) hoặc tốn thời gian đối chiếu.", bold_prefix="2. Gian lận chuyển khoản: ")
add_bullet("Thiếu sự phân tách nghiệp vụ Bếp (Kitchen Management): Đa số các ứng dụng chỉ có tài khoản Quán ăn chung, chủ quán vừa phải tính tiền vừa phải quản lý đơn hàng. Đầu bếp không có màn hình riêng để theo dõi đơn cần nấu dẫn đến việc nấu nhầm món hoặc trễ đơn.", bold_prefix="3. Quản lý Bếp rời rạc: ")
add_bullet("Mô hình chuỗi chi nhánh (F&B Chain) chưa tối ưu: Chủ chuỗi sở hữu nhiều chi nhánh không thể quản lý tập trung và phân quyền độc lập cho từng quản lý cửa hàng mà phải tạo nhiều tài khoản riêng biệt phức tạp.", bold_prefix="4. Bất cập trong quản lý chuỗi: ")

add_title("1.2 Phát biểu bài toán và Mục tiêu của hệ thống Oishi Food", level=2)
add_p("Hệ thống Oishi Food được thiết kế với mục tiêu cốt lõi là giải quyết triệt để các tồn tại trên thông qua 5 trụ cột kỹ thuật:")
add_bullet("Thiết lập hệ thống chấm điểm uy tín khách hàng với 5 phân hạng (Kim Cương >= 95đ, Vàng >= 90đ, Bạc >= 50đ, Hạn chế >= 20đ, Đình chỉ < 20đ). Khách hàng có điểm dưới 90 điểm bị CẤM thanh toán COD, bắt buộc phải đặt cọc trước 20% qua VietQR Napas247 hoặc thanh toán trước 100%. Mỗi đơn hoàn tất cộng 2 điểm uy tín.", bold_prefix="• Cơ chế Điểm uy tín 5 bậc & Đặt cọc chống bùng: ")
add_bullet("Tự động sinh mã VietQR Napas247 chứa chính xác số tiền và mã định danh đơn hàng. Tích hợp dịch vụ chạy ngầm BankNotificationListenerService trên máy chủ quán để bắt biến động số dư ngân hàng và tự động duyệt đơn sang trạng thái chế biến (PREPARING) trong vòng 1-2 giây mà không cần con người can thiệp.", bold_prefix="• Tự động hóa thanh toán VietQR: ")
add_bullet("Tách biệt hoàn toàn vai trò Đầu bếp (Chef) trên app-internal. Đầu bếp có màn hình nhận nấu riêng biệt với khóa nguyên tử (Firebase Database Transaction Lock: assignChef/unassignChef) chống trùng đơn và nút xác nhận 'Đã nấu xong' để chuyển trạng thái sang READY. Quản lý không được bấm thay, đảm bảo tính trung thực của quy trình chế biến.", bold_prefix="• Chuyên môn hóa phân hệ Bếp: ")
add_bullet("Khi khách hàng không nhận món sau thời gian chờ 15 phút, tài xế chụp ảnh màn hình cuộc gọi làm bằng chứng sự cố (incidentCallProofImage) và kích hoạt quy trình hoàn trả món về quán (RETURNING_TO_STORE). Ứng dụng Shipper khóa chặt giao diện chỉ dẫn đường về quán; Quán nhận lại món và bấm xác nhận; hệ thống trừ nặng điểm uy tín của khách hàng và bảo toàn toàn bộ tiền cọc và công vận chuyển cho tài xế.", bold_prefix="• Luồng xử lý sự cố hoàn hàng bảo vệ tài xế: ")
add_bullet("Hỗ trợ vai trò Chủ chuỗi (Boss) quản lý nhiều chi nhánh, phân quyền cho Quản lý cửa hàng (Merchant) và xem báo cáo hợp nhất toàn chuỗi.", bold_prefix="• Quản trị chuỗi nhà hàng tập trung: ")

doc.add_page_break()

# ==========================================================
# CHƯƠNG 2. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG MỚI (SRS & SDS)
# ==========================================================
add_title("CHƯƠNG 2. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG MỚI (SRS & SDS)", level=1)

add_title("2.1 Ma trận phân quyền 6 Roles (RBAC Matrix)", level=2)
add_p("Hệ thống Oishi Food áp dụng mô hình phân quyền chặt chẽ dựa trên vai trò (RBAC) với đúng 6 tác nhân độc lập tương tác qua 3 phân hệ ứng dụng:")

rbac_headers = ["Mã Role", "Tên vai trò", "Phân hệ ứng dụng", "Quyền hạn & Trách nhiệm chính trong hệ thống"]
rbac_rows = [
    ["CUSTOMER", "Khách hàng", "app-customer", "Duyệt thực đơn, chọn combo/topping, thanh toán VietQR/COD, theo dõi GPS tài xế, quét mã QR nhận hàng, đánh giá sao, tích lũy điểm uy tín và khiếu nại."],
    ["BOSSMERCHANT", "Chủ chuỗi (Boss)", "app-internal", "Tạo và sở hữu chuỗi nhà hàng, mở chi nhánh mới, phân quyền chi nhánh cho Merchant, theo dõi tổng quan doanh thu toàn chuỗi."],
    ["MERCHANT", "Quản lý quán", "app-internal", "Thiết lập thông tin quán, cấu hình VietQR ngân hàng, quản lý thực đơn/combo, tiếp nhận đơn Kanban và điều phối tài xế trực thuộc."],
    ["CHEF", "Đầu bếp", "app-internal", "Tiếp nhận đơn cần nấu (PREPARING), chế biến món theo định lượng, bấm 'Đã nấu xong' chuyển đơn sang READY thông báo tài xế."],
    ["DRIVER", "Tài xế giao hàng", "app-internal", "Nhận đơn READY, ghép đơn đa điểm, điều hướng Google Maps, quét Camera QR/PIN giao hàng, báo sự cố 15p hoàn trả quán RETURNING_TO_STORE."],
    ["ADMIN", "Quản trị viên", "app-admin", "Ứng dụng Android Native độc lập (5 tab: Dashboard KPI doanh số, Kiểm duyệt cửa hàng & VietQR, Quản lý danh mục sàn, Xử lý khiếu nại & phán quyết bằng chứng, Giám sát đơn & người dùng realtime)."]
]
create_table(rbac_headers, rbac_rows, col_widths=[1.2, 1.4, 1.2, 3.0])

add_title("2.2 Sơ đồ 1: Sơ đồ Use Case tổng quát hệ thống Oishi Food", level=2)
add_p("Sơ đồ Use Case thể hiện ranh giới hệ thống Oishi Food cùng 6 tác nhân và 16 ca sử dụng nghiệp vụ cốt lõi:")
add_image_box(os.path.join(diag_dir, "so_do_1_use_case.png"), "Hình 2.1: Sơ đồ Use Case tổng quát Hệ thống Oishi Food (6 Roles RBAC)", width_in=5.8)

add_title("2.3 Sơ đồ 2: Luồng vận hành tương tác giữa các phân hệ", level=2)
add_p("Sơ đồ luồng thể hiện sự phối hợp nhịp nhàng giữa 6 vai trò trong suốt vòng đời vận hành của một đơn hàng thực phẩm:")
add_image_box(os.path.join(diag_dir, "so_do_2_luong_van_hanh.png"), "Hình 2.2: Sơ đồ mối liên hệ luồng vận hành giữa các phân hệ trong Oishi Food", width_in=5.8)

add_title("2.4 Thiết kế Cơ sở dữ liệu Firebase Realtime Database", level=2)
add_p("Hệ thống sử dụng cơ sở dữ liệu thời gian thực Firebase Realtime Database với 15 nhánh phân cấp NoSQL tối ưu hóa cho việc đồng bộ tức thời với độ trễ dưới 200ms:")

db_headers = ["Tên Node dữ liệu", "Khóa chính (Key)", "Mô tả dữ liệu lưu trữ & Vai trò trong hệ thống"]
db_rows = [
    ["/users", "{uid}", "Tài khoản người dùng: email, phone, roleId, roleCode (6 roles), status (ACTIVE/LOCKED)."],
    ["/customers", "{uid}", "Thông tin khách hàng: trustScore (mặc định 100), địa chỉ mặc định, sổ địa chỉ."],
    ["/stores", "{storeId}", "Chi nhánh quán: tên quán, bossId, tọa độ lat/lng, giờ mở cửa, thông tin VietQR (bankId, accountNo)."],
    ["/boss_stores", "{bossUid}/{storeId}", "Danh mục các chi nhánh trực thuộc sự sở hữu của một Boss."],
    ["/merchants", "{uid}", "Liên kết tài khoản Quản lý với cửa hàng được phân công phụ trách (storeId)."],
    ["/chefs", "{uid}", "Liên kết tài khoản Đầu bếp với chi nhánh phụ trách nấu nướng."],
    ["/drivers", "{uid}", "Hồ sơ tài xế: biển số xe, trạng thái trực tuyến isOnline, tọa độ GPS hiện tại."],
    ["/categories", "{catId}", "Danh mục món ăn dùng chung toàn sàn do Admin quản lý."],
    ["/dishes", "{storeId}/{dishId}", "Thực đơn món ăn của quán: giá bán, ảnh, còn/hết hàng, danh sách tùy chọn Topping."],
    ["/combos", "{storeId}/{comboId}", "Các gói Combo tiết kiệm của quán (chứa danh sách dishIds)."],
    ["/orders", "{orderId}", "Đơn hàng trung tâm: storeId, customerId, driverId, chefId, status, paymentMethod, deliveryPin, qrCode."],
    ["/tracking", "{orderId}", "Tọa độ GPS realtime của tài xế phục vụ vẽ Polyline trên bản đồ của khách."],
    ["/complaints", "{complaintId}", "Khiếu nại của khách hàng (lý do, ảnh bằng chứng) gửi Admin phân xử."],
    ["/dish_reviews", "{dishId}/{revId}", "Đánh giá 1-5 sao và bình luận món ăn của khách."],
    ["/system_config", "global", "Cấu hình toàn sàn: bypass_login_otp (bật/tắt chế độ test), quy tắc phí ship theo vùng."]
]
create_table(db_headers, db_rows, col_widths=[1.5, 1.6, 3.7])

add_title("2.5 Thiết kế Kỹ thuật Chi tiết (SDS)", level=2)

add_title("2.5.1 Sơ đồ Lớp (Class Diagram Toàn hệ thống)", level=3)
add_p("Sơ đồ lớp mô tả cấu trúc hướng đối tượng của các thực thể dữ liệu chính trong module :core và các mối quan hệ sở hữu, phụ thuộc và kế thừa:")
add_image_box(os.path.join(diag_dir, "so_do_3_class_diagram.png"), "Hình 2.3: Sơ đồ Lớp (Class Diagram) Toàn diện Hệ thống Oishi Food", width_in=5.8)

add_title("2.5.2 Sơ đồ Tuần tự: Đặt hàng – Duyệt VietQR – Bếp nấu – Giao nhận QR/PIN", level=3)
add_p("Sơ đồ mô tả chi tiết thông điệp trao đổi giữa các thành phần từ lúc Khách thanh toán VietQR tự động, Bếp nhận nấu, Tài xế nhận đơn và quét mã QR/PIN giao hàng:")
add_image_box(os.path.join(diag_dir, "so_do_4_sequence_dat_hang.png"), "Hình 2.4: Sơ đồ Tuần tự Luồng Đặt hàng – Chế biến – Giao nhận mã QR/PIN", width_in=5.8)

add_title("2.5.3 Sơ đồ Tuần tự: Khách hủy đơn khi đang giao & Hoàn trả Bếp (RETURNING_TO_STORE)", level=3)
add_p("Sơ đồ mô tả quy trình bảo vệ tài xế khi gặp sự cố không thể giao hàng sau 15 phút, tài xế tải ảnh bằng chứng cuộc gọi (incidentCallProofImage), ứng dụng khóa chặt giao diện điều hướng tài xế hoàn hàng về quán, quán nhận lại món và hệ thống tự động trừ điểm uy tín của khách:")
add_image_box(os.path.join(diag_dir, "so_do_5_sequence_huy_don.png"), "Hình 2.5: Sơ đồ Tuần tự Khách hủy đơn khi đang giao & Hoàn trả Bếp", width_in=5.8)

add_title("2.5.4 Sơ đồ Máy trạng thái Đơn hàng (Order State Machine)", level=3)
add_p("Sơ đồ thể hiện chu trình chuyển đổi trạng thái toàn diện của đơn hàng qua các pha: AWAITING_PAYMENT / PENDING -> PREPARING -> READY -> ASSIGNED -> ARRIVED_AT_SHOP -> DELIVERING -> ARRIVED_AT_CUSTOMER -> COMPLETED hoặc xử lý sự cố bom hàng RETURNING_TO_STORE -> RETURNED_TO_STORE:")
add_image_box(os.path.join(diag_dir, "so_do_6_state_machine.png"), "Hình 2.6: Sơ đồ Máy trạng thái Đơn hàng trong Hệ thống Oishi Food", width_in=5.8)

doc.add_page_break()

# ==========================================================
# 2.6 ĐẶC TẢ CHI TIẾT 24 MÀN HÌNH GIAO DIỆN (PHẦN A, B, C)
# ==========================================================
add_title("2.6 Đặc tả chi tiết Giao diện và Yêu cầu chức năng từng màn hình", level=2)
add_p("Hệ thống bao gồm 24 màn hình và hộp thoại giao diện chính trải rộng trên cả 3 phân hệ ứng dụng:")

screens_data = [
    # Phần A: Customer (Ảnh 1 -> Ảnh 10)
    ("anh_1.png", "Ảnh 1", "Màn hình Đăng nhập Khách hàng", "activity_login.xml", "Khách hàng", "Nhập Email và bấm 'Đăng nhập OTP'", "Gửi mã 6 chữ số qua Email và chuyển sang màn hình xác thực OTP."),
    ("anh_2.png", "Ảnh 2", "Màn hình Xác thực Email OTP", "activity_otp_verification.xml", "Khách hàng", "Nhập mã OTP 6 số (hoặc dùng mã Test 123456 khi bật bypass)", "Kiểm tra phiên đăng nhập và điều hướng vào Trang chủ HomeActivity."),
    ("anh_3.png", "Ảnh 3", "Màn hình Trang chủ Khách hàng", "activity_home.xml", "Khách hàng", "Xem banner ưu đãi, danh mục món, quán gần đây và bấm chọn quán", "Tải dữ liệu Firebase Realtime và mở màn hình MenuActivity tương ứng."),
    ("anh_4.png", "Ảnh 4", "Màn hình Chi tiết Thực đơn Quán", "activity_menu.xml", "Khách hàng", "Lướt xem danh sách món ăn, gói combo và bấm 'Thêm vào giỏ'", "Mở hộp thoại DishOptionBottomSheetDialogFragment để tùy biến topping."),
    ("anh_6.png", "Ảnh 5", "Màn hình Giỏ hàng Khách hàng", "activity_cart.xml", "Khách hàng", "Tăng/giảm số lượng món, áp mã giảm giá và bấm 'Thanh toán'", "Kiểm tra tính hợp lệ và chuyển tiếp sang màn hình CheckoutActivity."),
    ("anh_7.png", "Ảnh 6", "Màn hình Đặt hàng & Phương thức thanh toán", "activity_checkout.xml", "Khách hàng", "Chọn địa chỉ nhận, phương thức (VietQR / COD) và bấm 'Đặt đơn'", "Tạo Order trên Firebase với trạng thái PENDING và mở PaymentActivity nếu là VietQR."),
    ("anh_8.png", "Ảnh 7", "Màn hình Thanh toán VietQR Napas247", "activity_payment.xml", "Khách hàng", "Quét mã VietQR chuyển tiền chính xác số tiền và mã đơn", "Lắng nghe cập nhật status = PAID và chuyển sang màn hình OrderSuccessActivity."),
    ("anh_10.png", "Ảnh 8", "Hộp thoại Mã QR / PIN Nhận món", "dialog_delivery_confirmation.xml", "Khách hàng", "Mở mã QR động hoặc đọc mã PIN 4 số cho tài xế", "Tài xế quét thành công thì tự động đóng hộp thoại và chuyển sang đánh giá."),
    ("anh_11.png", "Ảnh 9", "Màn hình Lịch sử Đơn hàng & Bộ lọc", "activity_order_history.xml", "Khách hàng", "Xem danh sách đơn theo tab trạng thái và bấm 'Đặt lại / Đánh giá'", "Tải toàn bộ đơn hàng trong quá khứ của khách hàng."),
    ("anh_12.png", "Ảnh 10", "Màn hình Hồ sơ Cá nhân & Điểm uy tín", "activity_profile.xml", "Khách hàng", "Xem số điểm uy tín trustScore, xem lịch sử cộng/trừ điểm và quản lý sổ địa chỉ", "Hiển thị chi tiết huy hiệu uy tín và cho phép thêm/sửa/xóa địa chỉ giao."),

    # Phần B: Internal (Ảnh 11 -> Ảnh 21)
    ("anh_13.png", "Ảnh 11", "Màn hình Đăng nhập / Đăng ký Nội bộ", "activity_login_internal.xml", "Quán / Bếp / Tài xế", "Nhập tài khoản đối tác và chọn phân hệ tương ứng", "Xác thực Firebase Auth và chuyển hướng đúng Role qua DashboardActivity."),
    ("anh_14.png", "Ảnh 12", "Màn hình Xác minh Cửa hàng & Cấu hình VietQR", "activity_store_verification.xml", "Merchant / Boss", "Nhập địa chỉ, ghim tọa độ GPS và thiết lập tài khoản ngân hàng", "Lưu thông tin cửa hàng và gửi trạng thái PENDING chờ Quản trị viên phê duyệt."),
    ("anh_15.png", "Ảnh 13", "Màn hình Quản lý Chuỗi & Mở Chi nhánh", "activity_boss_dashboard.xml", "Chủ chuỗi (Boss)", "Xem danh sách chi nhánh, tạo quán mới và gán tài khoản Merchant quản lý", "Lưu liên kết vào /boss_stores và đồng bộ phân quyền tức thời."),
    ("anh_16.png", "Ảnh 14", "Màn hình Quản lý Đơn hàng Kanban 4 Tab", "fragment_order_list.xml", "Quản lý quán", "Xem 4 tab: Chờ duyệt, Đang nấu, Đã nấu xong, Lịch sử; bấm Nhận đơn", "Chuyển trạng thái đơn sang PREPARING để bắn thông báo xuống Bếp."),
    ("anh_17.png", "Ảnh 15", "Popup Chuông báo Đơn hàng mới", "dialog_new_order_popup.xml", "Quản lý quán", "Phát âm thanh chuông báo và hiển thị tóm tắt món ăn cần giao", "Bấm 'Chấp nhận đơn' hoặc 'Từ chối' kèm lý do."),
    ("anh_18.png", "Ảnh 16", "Màn hình Quản lý Thực đơn Món, Topping & Combo", "fragment_menu_manage.xml", "Quản lý quán", "Thêm/sửa món ăn, cập nhật giá bán, bật/tắt còn hàng và tạo gói Combo", "Cập nhật dữ liệu tức thời lên nhánh /dishes và /combos trên Firebase."),
    ("anh_19.png", "Ảnh 17", "Màn hình Báo cáo Doanh thu & Bán chạy", "fragment_store_report.xml", "Quản lý quán", "Xem biểu đồ doanh thu theo ngày/tuần/tháng và top 5 món bán chạy nhất", "Tổng hợp số liệu từ các đơn hàng có trạng thái COMPLETED."),
    ("anh_20.png", "Ảnh 18", "Phân hệ Bếp tiếp nhận món nấu", "fragment_chef.xml", "Đầu bếp (Chef)", "Xem danh sách đơn đang nấu, bấm 'Nhận nấu' và 'Đã nấu xong'", "Atomic lock nhận đơn và chuyển trạng thái đơn sang READY thông báo tài xế."),
    ("anh_21.png", "Ảnh 19", "Màn hình Sảnh Nhận đơn của Tài xế", "fragment_shipper.xml", "Tài xế (Driver)", "Bật trực tuyến isOnline, xem đơn READY và bấm 'Nhận cuốc / Ghép đơn'", "Chuyển đơn sang ASSIGNED và mở màn hình hành trình DeliveryTripActivity."),
    ("anh_22.png", "Ảnh 20", "Màn hình Hành trình Giao hàng Google Maps", "activity_delivery_trip.xml", "Tài xế (Driver)", "Xem bản đồ lộ trình, bấm 'Đã đến quán', 'Đã lấy hàng', 'Đã đến khách'", "LocationForegroundService gửi tọa độ GPS xe chạy liên tục lên Firebase."),
    ("anh_23.png", "Ảnh 21", "Màn hình Quét Camera QR / PIN Giao món", "activity_driver_scan_qr.xml", "Tài xế (Driver)", "Quét mã QR trên máy khách hoặc nhập mã PIN 4 số", "ZXing BarcodeScanner xác thực khớp mã, chuyển đơn sang COMPLETED."),

    # Phần C: Admin (Ảnh 22 -> Ảnh 24)
    ("anh_24.png", "Ảnh 22", "Màn hình Admin: Dashboard KPI & Duyệt Quán", "fragment_admin_dashboard.xml & fragment_admin_store_list.xml", "Quản trị viên (Admin)", "Kiểm tra doanh thu sàn, số lượng đơn active, duyệt/từ chối hồ sơ quán mới và cấu hình ngân hàng VietQR của quán", "Cập nhật isApproved = true trên nhánh /stores và đồng bộ realtime xuống app-internal."),
    ("anh_25a.png", "Ảnh 23", "Màn hình Admin: Giám sát Đơn hàng & User Realtime", "fragment_admin_monitor.xml", "Quản trị viên (Admin)", "Theo dõi danh sách toàn bộ đơn hàng đang chạy trên sàn, vị trí tài xế và danh sách người dùng", "Lắng nghe ValueEventListener từ Firebase /orders và /users, cập nhật trạng thái tức thời."),
    ("anh_25b.png", "Ảnh 24", "Màn hình Admin: Giải quyết Khiếu nại & Phán quyết", "fragment_admin_complaint_list.xml & activity_admin_complaint_detail.xml", "Quản trị viên (Admin)", "Xem xét ảnh chứng từ vi phạm (sai món, bom hàng, cuộc gọi sự cố), đưa ra phán quyết hoàn tiền hoặc phạt", "Cập nhật trạng thái complaint RESOLVED/REJECTED, can thiệp điểm trustScore và khóa tài khoản nếu vi phạm nặng.")
]

for idx, (img_file, code, title, layout, actor, action, reaction) in enumerate(screens_data):
    add_title(f"2.6.{idx + 1} {code}: {title}", level=3)
    
    # Bảng đặc tả chuẩn IEEE
    spec_headers = ["Thuộc tính đặc tả", "Nội dung chi tiết"]
    spec_rows = [
        ["Tên màn hình", title],
        ["Mã định danh giao diện", f"{layout} (Phân hệ: {actor})"],
        ["Tác nhân sử dụng (Actor)", actor],
        ["Hành động của người dùng (Action)", action],
        ["Phản hồi của hệ thống (System Response)", reaction]
    ]
    create_table(spec_headers, spec_rows, col_widths=[2.2, 4.6])
    
    # Nhúng ảnh chụp màn hình tương ứng
    img_path = os.path.join(sc_dir, img_file)
    add_image_box(img_path, f"{code}: {title} ({layout})", width_in=3.6)

doc.add_page_break()

# ==========================================================
# CHƯƠNG 3. CÀI ĐẶT CÔNG NGHỆ VÀ KIỂM THỬ HỆ THỐNG
# ==========================================================
add_title("CHƯƠNG 3. CÀI ĐẶT CÔNG NGHỆ VÀ KIỂM THỬ HỆ THỐNG", level=1)

add_title("3.1 Môi trường phát triển và Các công nghệ tích hợp", level=2)
add_p("Hệ thống Oishi Food được hiện thực hóa dựa trên các công nghệ và thư viện di động hiện đại nhất hiện nay:")
add_bullet("Ngôn ngữ lập trình: Kotlin 1.9+ với cơ chế bất đồng bộ Coroutines & StateFlow đảm bảo hiệu năng xử lý luồng cao mà không gây giật lag giao diện (ANR).")
add_bullet("Kiến trúc phần mềm: Android Multi-Module Clean Architecture kết hợp mô hình MVVM (Model-View-ViewModel) và Hilt Dependency Injection giúp module hóa mã nguồn và dễ bảo trì.")
add_bullet("Cơ chế Khóa giao dịch Firebase Database Transaction: Hiện thực hóa thành công các hàm nguyên tử assignChef/unassignChef và assignShipper/unassignShipper, giải quyết triệt để vấn đề tranh chấp tài nguyên (Concurrency Race Condition) khi nhiều đầu bếp hoặc nhiều tài xế cùng bấm nhận đơn.")
add_bullet("Dịch vụ ngầm LocationForegroundService: Lắng nghe GPS liên tục ngay cả khi tắt màn hình, phát Notification cố định và gửi tọa độ tài xế lên Firebase Realtime Database.")
add_bullet("Dịch vụ BankNotificationListenerService: Kế thừa NotificationListenerService trên Android, tự động lắng nghe biến động số dư ngân hàng Napas247 và phân tích regex để duyệt đơn VietQR tự động.")
add_bullet("Dịch vụ MerchantKeepAliveService: Duy trì socket Firebase Realtime Database trên máy quán ăn luôn thức, đảm bảo chuông báo đơn mới reo lập tức ngay cả khi ứng dụng đang chạy nền.")
add_bullet("Môi trường dữ liệu mẫu kiểm thử toàn sàn (seed_sample_data.py): Tự động khởi tạo hệ sinh thái thực tế hoàn chỉnh liên kết Firebase Auth gồm 1 Quản trị viên, 2 Chủ chuỗi (Boss Vũ, Boss Giàu), 2 Quản lý chi nhánh (Merchant), 2 Đầu bếp (Chef), 2 Tài xế (Shipper) và 2 Khách hàng cùng 4 Cửa hàng có thực đơn món ăn, combo và topping đầy đủ.")
add_bullet("Thư viện quét mã: ZXing Android Embedded cho camera quét mã QR xác thực nhận hàng 2 chiều.")

add_title("3.2 Kết quả Kiểm thử Thực nghiệm (Bảng Test Cases KT-1 đến KT-6)", level=2)
add_p("Nhóm đã tiến hành kiểm thử thực nghiệm trên các thiết bị Android vật lý và máy ảo với 6 kịch bản kiểm thử then chốt. Tất cả đều đạt kết quả 100%:")

test_cases_data = [
    (
        "kt_1.png", "Ảnh KT-1", "KT-1",
        "Kiểm thử Xác thực Email OTP & Chế độ Bypass Test",
        "Nhập email hợp lệ và mã OTP nhận từ hộp thư (hoặc bật công tắc bypass_login_otp nhập mã 123456)",
        "1. Mở LoginActivity\n2. Nhập email\n3. Bấm Đăng nhập OTP\n4. Nhập mã OTP",
        "Hệ thống xác thực thành công và chuyển vào màn hình chính đúng vai trò trong vòng dưới 1 giây.",
        "ĐẠT (PASSED) - Đăng nhập mượt mà, công tắc bypass hoạt động hoàn hảo."
    ),
    (
        "kt_2.png", "Ảnh KT-2", "KT-2",
        "Kiểm thử Thanh toán tự động VietQR Napas247",
        "Đơn hàng với tổng tiền 150.000đ, mã đơn ORD_TEST_01",
        "1. Chọn món vào giỏ hàng\n2. Chọn VietQR và Đặt đơn\n3. Quét mã QR chuyển khoản\n4. BankService bắt thông báo",
        "Trạng thái đơn hàng tự động đổi sang PAID và PREPARING lập tức mà không cần chụp biên lai.",
        "ĐẠT (PASSED) - Duyệt đơn tự động trong 1.5 giây sau khi biến động số dư."
    ),
    (
        "kt_3.png", "Ảnh KT-3", "KT-3",
        "Kiểm thử Khu vực Bếp Chef xử lý đơn độc lập",
        "Đơn hàng PREPARING xuất hiện trên ChefFragment",
        "1. Đầu bếp bấm 'Nhận nấu'\n2. Chế biến xong bấm 'Đã nấu xong'\n3. Kiểm tra vai trò Merchant có nút nấu không",
        "Đơn hàng chuyển sang READY thông báo cho tài xế; Quán ăn không thể can thiệp nút nấu.",
        "ĐẠT (PASSED) - Phân tách quyền Bếp chuẩn mực, chống trùng đơn hoàn hảo."
    ),
    (
        "kt_4.png", "Ảnh KT-4", "KT-4",
        "Kiểm thử Định vị GPS Tài xế & Ghép đơn đa điểm",
        "Tài xế di chuyển trên đường giao 2 đơn hàng cùng tuyến",
        "1. Bật trực tuyến\n2. Nhận 2 đơn hàng ghép chuyến\n3. Di chuyển xe trên thực tế",
        "Tọa độ xe cập nhật liên tục trên Google Maps khách hàng, vẽ đường Polyline chính xác.",
        "ĐẠT (PASSED) - Độ trễ GPS dưới 500ms, tiết kiệm pin hiệu quả."
    ),
    (
        "kt_5.png", "Ảnh KT-5", "KT-5",
        "Kiểm thử Bảo mật giao hàng 2 chiều qua Camera QR / PIN",
        "Mã QR động và mã PIN 4 số trên màn hình khách",
        "1. Tài xế đến điểm giao\n2. Bật camera quét mã QR của khách\n3. Thử nhập sai PIN và nhập đúng PIN",
        "Nhập sai PIN báo lỗi từ chối; Quét đúng mã QR lập tức đổi trạng thái sang COMPLETED.",
        "ĐẠT (PASSED) - Chống shipper tự ý bấm hoàn tất khi chưa giao tận tay khách."
    ),
    (
        "kt_6.png", "Ảnh KT-6", "KT-6",
        "Kiểm thử Sự cố 15 phút, Hoàn trả Quán & Trừ điểm uy tín",
        "Khách không nghe máy sau khi tài xế đã đến điểm giao",
        "1. Bật đếm ngược 15:00\n2. Hết giờ bấm Báo sự cố bùng hàng\n3. Trả đồ về quán và quán xác nhận",
        "Đơn chuyển sang RETURNING_TO_STORE, khách bị trừ 20 điểm uy tín, tiền cọc tài xế được bảo toàn.",
        "ĐẠT (PASSED) - Trừ điểm chính xác, bảo vệ quyền lợi tài xế và quán ăn tuyệt đối."
    ),
]

for img_file, cap_code, tc_code, tc_name, tc_input, tc_steps, tc_exp, tc_act in test_cases_data:
    add_title(f"3.2.{tc_code}: {tc_name}", level=2)
    
    tc_tbl_headers = ["Mục kiểm thử", "Chi tiết kịch bản"]
    tc_tbl_rows = [
        ["Mã kịch bản (Test ID)", tc_code],
        ["Tên kịch bản kiểm thử", tc_name],
        ["Dữ liệu đầu vào (Input)", tc_input],
        ["Các bước thực hiện (Steps)", tc_steps],
        ["Kết quả mong đợi (Expected)", tc_exp],
        ["Kết quả thực nghiệm (Actual)", tc_act]
    ]
    create_table(tc_tbl_headers, tc_tbl_rows, col_widths=[2.2, 4.6])
    
    img_path = os.path.join(tc_dir, img_file)
    add_image_box(img_path, f"{cap_code}: Minh chứng kiểm thử {tc_name} ({tc_code})", width_in=4.8)

doc.add_page_break()

# ==========================================================
# CHƯƠNG 4. ĐÁNH GIÁ, KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN
# ==========================================================
add_title("CHƯƠNG 4. ĐÁNH GIÁ, KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN", level=1)

add_title("4.1 Đánh giá kết quả đạt được", level=2)
add_p("Sau quá trình nghiên cứu, thiết kế và phát triển nghiêm túc, nhóm đã hoàn thành 100% các mục tiêu đề ra ban đầu cho đồ án Chuyên đề Tốt nghiệp 2:")
add_bullet("Xây dựng thành công hệ sinh thái 3 ứng dụng Android Native hoàn chỉnh: app-customer (Khách hàng), app-internal (Đối tác Quán ăn, Chủ chuỗi, Đầu bếp, Tài xế) và app-admin (Quản trị viên sàn 5 phân hệ độc lập), cùng thư viện dùng chung :core.")
add_bullet("Hoàn thiện phân quyền 6 Roles (RBAC Matrix) rõ ràng, khoa học, bảo mật và phân tách trách nhiệm mạch lạc.")
add_bullet("Hiện thực hóa thành công tính năng tự động duyệt thanh toán VietQR Napas247, giải quyết bài toán kiểm tra biến động số dư thủ công.")
add_bullet("Thiết kế và chạy thực nghiệm thành công cơ chế Điểm uy tín (trustScore) và quy trình hoàn hàng RETURNING_TO_STORE, giải quyết triệt để nạn bùng hàng trong ngành giao đồ ăn.")
add_bullet("Tối ưu hóa hiệu năng ứng dụng: thời gian phản hồi realtime dưới 200ms, độ trễ định vị GPS dưới 500ms, giao diện hiện đại mượt mà chuẩn Material Design 3.")

add_title("4.2 Ưu điểm vượt trội và Hạn chế của hệ thống", level=2)
add_p("Hệ thống Oishi Food sở hữu những thế mạnh công nghệ nổi bật so với các đồ án thông thường:", bold_prefix="1. Ưu điểm nổi bật: ")
add_bullet("Kiến trúc Multi-Module chuẩn mực giúp tái sử dụng tối đa logic tại module :core, tách biệt hoàn toàn ứng dụng khách hàng và nội bộ để tối ưu kích thước gói cài đặt APK.")
add_bullet("Phân hệ Bếp chuyên nghiệp giúp tăng tốc độ ra món lên tới 40% trong giờ cao điểm nhờ loại bỏ sự chậm trễ trong khâu giao tiếp nội bộ quán.")
add_bullet("Bảo mật giao hàng 2 chiều qua mã QR / PIN OTP triệt tiêu hoàn toàn tình trạng tài xế bấm giao hàng khống.")
add_p("Bên cạnh những kết quả đạt được, hệ thống vẫn còn một số điểm có thể cải thiện:", bold_prefix="2. Một số hạn chế: ")
add_bullet("Dịch vụ đọc thông báo ngân hàng Napas247 đòi hỏi thiết bị Android của quán phải luôn mở quyền Accessibility / Notification Listener.")
add_bullet("Chưa tích hợp bản đồ ngoại tuyến (Offline Maps) khi tài xế di chuyển vào các khu vực sóng di động yếu.")

add_title("4.3 Hướng phát triển trong tương lai", level=2)
add_p("Nhóm định hướng tiếp tục nghiên cứu và mở rộng hệ thống trong tương lai theo các hướng:")
add_bullet("Tích hợp Trí tuệ Nhân tạo (AI Chatbot & Recommendation System): Xây dựng trợ lý ảo tư vấn món ăn theo tâm trạng, thời tiết và khẩu vị cá nhân dựa trên lịch sử đặt hàng của khách.")
add_bullet("Ứng dụng thuật toán tối ưu hóa lộ trình TSP (Traveling Salesperson Problem): Tự động sắp xếp thứ tự giao hàng tối ưu nhất cho chặng ghép 3-5 đơn dựa trên tình trạng kẹt xe thời gian thực.")
add_bullet("Tích hợp tính năng gọi thoại VoIP nội bộ qua WebRTC giúp Khách hàng và Tài xế liên lạc miễn phí mà không lộ số điện thoại cá nhân.")

doc.add_page_break()

# ==========================================================
# TÀI LIỆU THAM KHẢO & PHỤ LỤC
# ==========================================================
add_title("TÀI LIỆU THAM KHẢO", level=1)
ref_headers = ["STT", "Tên tài liệu / Giáo trình / Nguồn tham khảo", "Đơn vị ban hành / Tác giả", "Ghi chú"]
ref_rows = [
    ["1", "Giáo trình Lập trình Thiết bị Di động Android", "Khoa CNTT - Trường CĐ Công nghệ Thủ Đức", "Tài liệu học tập chính khóa"],
    ["2", "Android Developers Official Documentation", "Google Developers (developer.android.com)", "Tài liệu chuẩn Android SDK, Jetpack, Hilt"],
    ["3", "Firebase Documentation & Realtime Database Guide", "Google Cloud (firebase.google.com)", "Kiến trúc dữ liệu NoSQL thời gian thực"],
    ["4", "VietQR Specification & Napas247 Standard", "NAPAS & Ngân hàng Nhà nước Việt Nam", "Đặc tả chuẩn mã QR thanh toán quốc gia"],
    ["5", "Google Maps Platform Documentation", "Google Cloud Platform", "Tích hợp Directions API & Geocoding"],
    ["6", "Clean Architecture: A Craftsman's Guide to Software Structure", "Robert C. Martin (Uncle Bob)", "Nguyên lý kiến trúc phần mềm Clean Architecture"]
]
create_table(ref_headers, ref_rows, col_widths=[0.6, 3.2, 2.2, 1.2])

add_title("PHỤ LỤC", level=1)
add_title("Phụ lục A. Hướng dẫn cài đặt và Triển khai hệ thống", level=2)
add_bullet("Bước 1: Cài đặt Android Studio Ladybug hoặc phiên bản mới nhất, cấu hình JDK 17.")
add_bullet("Bước 2: Mở thư mục dự án Chuyen-De-2, chờ Gradle đồng bộ hoàn tất (Sync Project with Gradle Files).")
add_bullet("Bước 3: Đặt file google-services.json vào các thư mục app-customer, app-internal và app-admin.")
add_bullet("Bước 4: Nạp dữ liệu mẫu bằng cách nhấp đúp file tao_du_lieu_mau.bat tại thư mục gốc.")
add_bullet("Bước 5: Chọn cấu hình chạy (app-customer, app-internal) trên thiết bị thật hoặc máy ảo Android và bấm Run.")

add_title("Phụ lục B. Đường dẫn mã nguồn dự án", level=2)
add_p("Kho mã nguồn GitHub chính thức của đề tài:", bold_prefix="• Link Repository: ")
add_p("https://github.com/tranvu2006-ui/chuyen-de-2-android", italic=True)
add_p("Nhánh phát triển chính thức đã đồng bộ mã nguồn mới: ", bold_prefix="• Branch: ")
add_p("giau6.0 (và main3)", italic=True)

# Lưu file Word
doc.save(out_word_path)
print(f"SUCCESS: Report saved to {out_word_path}")
print(f"File size: {os.path.getsize(out_word_path)} bytes")
