import os
import sys
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

out_word_path = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\BAO_CAO_TOT_NGHIEP_OISHI_FOOD_CHUAN_TDC.docx"
docs_dir = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\docs"
diag_dir = os.path.join(docs_dir, "diagrams")
sc_dir = os.path.join(docs_dir, "screenshots")
tc_dir = os.path.join(docs_dir, "test_cases")

doc = docx.Document()

# 1. Cấu hình Margins chuẩn TDC: Trái 3cm (1.18 in), Phải 2cm (0.79 in), Trên 2cm (0.79 in), Dưới 2cm (0.79 in)
sections = doc.sections
for section in sections:
    section.top_margin = Inches(0.79)
    section.bottom_margin = Inches(0.79)
    section.left_margin = Inches(1.18)
    section.right_margin = Inches(0.79)

# 2. Cấu hình Style mặc định font Times New Roman
normal_style = doc.styles['Normal']
normal_style.font.name = 'Times New Roman'
normal_style.font.size = Pt(13)
normal_style.font.color.rgb = RGBColor(0x11, 0x18, 0x27)
normal_style.paragraph_format.line_spacing = 1.3
normal_style.paragraph_format.space_after = Pt(6)

# Cấu hình chuẩn Heading Styles để hiển thị đầy đủ trên Navigation Pane (Cây mục lục Word)
h1_style = doc.styles['Heading 1']
h1_style.font.name = 'Times New Roman'
h1_style.font.size = Pt(15)
h1_style.font.bold = True
h1_style.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A) # Dark Navy
h1_style.paragraph_format.space_before = Pt(14)
h1_style.paragraph_format.space_after = Pt(6)
h1_style.paragraph_format.keep_with_next = True

h2_style = doc.styles['Heading 2']
h2_style.font.name = 'Times New Roman'
h2_style.font.size = Pt(13.5)
h2_style.font.bold = True
h2_style.font.color.rgb = RGBColor(0x1E, 0x29, 0x3B)
h2_style.paragraph_format.space_before = Pt(10)
h2_style.paragraph_format.space_after = Pt(4)
h2_style.paragraph_format.keep_with_next = True

h3_style = doc.styles['Heading 3']
h3_style.font.name = 'Times New Roman'
h3_style.font.size = Pt(13)
h3_style.font.bold = True
h3_style.font.color.rgb = RGBColor(0x33, 0x41, 0x55)
h3_style.paragraph_format.space_before = Pt(6)
h3_style.paragraph_format.space_after = Pt(2)
h3_style.paragraph_format.keep_with_next = True

def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
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

def add_title(text, level=1, space_before=12, space_after=6):
    # Sử dụng doc.add_heading và gắn rõ ràng outlineLvl để 100% Word hiển thị trên thanh Navigation Pane
    p = doc.add_heading(level=level)
    p.paragraph_format.space_before = Pt(space_before)
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.keep_with_next = True
    
    # Gắn outlineLvl vào pPr (0 cho Heading 1, 1 cho Heading 2, 2 cho Heading 3)
    pPr = p._p.get_or_add_pPr()
    outlineLvl = parse_xml(f'<w:outlineLvl {nsdecls("w")} w:val="{level-1}"/>')
    pPr.append(outlineLvl)
    
    run = p.add_run(text)
    run.font.name = 'Times New Roman'
    run.bold = True
    if level == 1:
        run.font.size = Pt(15)
        run.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A)
    elif level == 2:
        run.font.size = Pt(13.5)
        run.font.color.rgb = RGBColor(0x1E, 0x29, 0x3B)
    elif level == 3:
        run.font.size = Pt(13)
        run.font.color.rgb = RGBColor(0x33, 0x41, 0x55)
    return p

def add_p(text, bold_prefix="", italic=False, align=WD_ALIGN_PARAGRAPH.JUSTIFY):
    p = doc.add_paragraph()
    p.alignment = align
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(6)
    if bold_prefix:
        r_pre = p.add_run(bold_prefix)
        r_pre.font.name = 'Times New Roman'
        r_pre.font.size = Pt(13)
        r_pre.bold = True
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    r.font.size = Pt(13)
    r.italic = italic
    return p

def add_bullet(text, bold_prefix=""):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.line_spacing = 1.25
    p.paragraph_format.space_after = Pt(4)
    if bold_prefix:
        r_pre = p.add_run(bold_prefix)
        r_pre.font.name = 'Times New Roman'
        r_pre.font.size = Pt(13)
        r_pre.bold = True
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    r.font.size = Pt(13)
    return p

def add_image_box(img_path, caption, width_in=5.8):
    if os.path.exists(img_path):
        p_img = doc.add_paragraph()
        p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_img.paragraph_format.space_before = Pt(8)
        p_img.paragraph_format.space_after = Pt(3)
        run_img = p_img.add_run()
        run_img.add_picture(img_path, width=Inches(width_in))
        
        p_cap = doc.add_paragraph()
        p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_cap.paragraph_format.space_before = Pt(0)
        p_cap.paragraph_format.space_after = Pt(10)
        p_cap.paragraph_format.keep_with_next = False
        r_cap = p_cap.add_run(caption)
        r_cap.font.name = 'Times New Roman'
        r_cap.font.size = Pt(11)
        r_cap.italic = True
        r_cap.bold = True
        r_cap.font.color.rgb = RGBColor(0x33, 0x41, 0x55)
    else:
        add_p(f"[CHƯA TÌM THẤY ẢNH: {caption}]", italic=True, align=WD_ALIGN_PARAGRAPH.CENTER)

def create_table(headers, rows, col_widths=None):
    tbl = doc.add_table(rows=len(rows) + 1, cols=len(headers))
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False

    # Header Row
    hdr_cells = tbl.rows[0].cells
    for i, title in enumerate(headers):
        hdr_cells[i].text = title
        set_cell_background(hdr_cells[i], "1E293B")
        set_cell_margins(hdr_cells[i], top=120, bottom=120, left=140, right=140)
        hdr_cells[i].vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p = hdr_cells[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        for r in p.runs:
            r.font.name = 'Times New Roman'
            r.font.size = Pt(11)
            r.bold = True
            r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)

    # Data Rows
    for r_idx, row in enumerate(rows):
        row_cells = tbl.rows[r_idx + 1].cells
        bg_col = "F8FAFC" if r_idx % 2 == 1 else "FFFFFF"
        for c_idx, val in enumerate(row):
            row_cells[c_idx].text = str(val)
            set_cell_background(row_cells[c_idx], bg_col)
            set_cell_margins(row_cells[c_idx], top=80, bottom=80, left=120, right=120)
            row_cells[c_idx].vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            p = row_cells[c_idx].paragraphs[0]
            if c_idx == 0 or len(str(val)) <= 8:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            else:
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            for r in p.runs:
                r.font.name = 'Times New Roman'
                r.font.size = Pt(10.5)

    if col_widths:
        for row in tbl.rows:
            for idx, width in enumerate(col_widths):
                row.cells[idx].width = Inches(width)

    doc.add_paragraph().paragraph_format.space_after = Pt(8)
    return tbl

def add_table_of_contents():
    p = doc.add_paragraph()
    r = p.add_run()
    fldChar1 = parse_xml(r'<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w'))
    instrText = parse_xml(r'<w:instrText %s xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText>' % nsdecls('w'))
    fldChar2 = parse_xml(r'<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w'))
    fldChar3 = parse_xml(r'<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w'))
    r._r.append(fldChar1)
    r._r.append(instrText)
    r._r.append(fldChar2)
    r._r.append(fldChar3)

print("Updated builder_base with true Word Headings & TOC support!")
