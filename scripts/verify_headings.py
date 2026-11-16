import docx

doc = docx.Document(r'c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\BAO_CAO_TOT_NGHIEP_OISHI_FOOD_CHUAN_TDC.docx')
ol_count = 0
for i, p in enumerate(doc.paragraphs):
    pPr = p._p.find('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}pPr')
    if pPr is not None:
        ol = pPr.find('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}outlineLvl')
        if ol is not None:
            ol_count += 1
            if ol_count <= 10:
                val = ol.get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val')
                print(f"p[{i}] outlineLvl={val} (Heading {int(val)+1}): {p.text[:50]}")

print(f"Total paragraphs with outlineLvl: {ol_count}")
