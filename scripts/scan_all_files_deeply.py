import os
import re

base_dir = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android"
modules = ["core", "app-customer", "app-internal", "app-admin"]

report = []
report.append("="*80)
report.append("BẢN QUÉT CHI TIẾT TOÀN BỘ 100% CÁC FILE TRONG HỆ THỐNG OISHI FOOD (3 APPS NATIVE + CORE)")
report.append("="*80 + "\n")

all_files_count = 0
all_code_lines = 0

for mod in modules:
    mod_path = os.path.join(base_dir, mod)
    report.append(f"\n################################################################################")
    report.append(f"MODULE: {mod}")
    report.append(f"################################################################################\n")
    
    for root, dirs, files in os.walk(mod_path):
        if "build" in root or ".gradle" in root:
            continue
        
        # Sắp xếp để dễ theo dõi
        files = sorted([f for f in files if f.endswith(".kt") or f.endswith(".xml")])
        for f in files:
            full_path = os.path.join(root, f)
            rel_path = os.path.relpath(full_path, start=base_dir)
            all_files_count += 1
            
            with open(full_path, "r", encoding="utf-8", errors="ignore") as file:
                lines = file.readlines()
            all_code_lines += len(lines)
            
            if f.endswith(".kt"):
                content = "".join(lines)
                classes = re.findall(r"(?:class|object|interface)\s+([A-Za-z0-9_]+)", content)
                methods = re.findall(r"fun\s+([A-Za-z0-9_]+)\s*\(", content)
                
                # Trích xuất header comment hoặc mô tả chức năng
                doc_comment = ""
                m_doc = re.search(r"/\*\*(.*?)\*/", content, re.DOTALL)
                if m_doc:
                    raw_doc = m_doc.group(1).strip()
                    doc_lines = [l.strip().lstrip("* ").strip() for l in raw_doc.split("\n") if l.strip().lstrip("* ").strip()]
                    doc_comment = " | ".join(doc_lines[:3])
                
                report.append(f"FILE: {rel_path} ({len(lines)} dòng)")
                if classes:
                    report.append(f"  - Classes/Objects: {', '.join(classes)}")
                if doc_comment:
                    report.append(f"  - Mô tả: {doc_comment}")
                if methods:
                    report.append(f"  - Các hàm chính ({len(methods)} hàm): {', '.join(methods[:8])}")
                report.append("")
                
            elif f.endswith(".xml") and "layout" in root:
                # Trích xuất các View ID quan trọng
                content = "".join(lines)
                ids = re.findall(r'android:id="@+id/([A-Za-z0-9_]+)"', content)
                report.append(f"LAYOUT: {rel_path} ({len(lines)} dòng)")
                if ids:
                    report.append(f"  - View IDs: {', '.join(ids[:10])}")
                report.append("")

out_summary = os.path.join(base_dir, "docs", "HE_THONG_THUC_TE_3_APPS_VA_CORE.txt")
with open(out_summary, "w", encoding="utf-8") as out:
    out.write("\n".join(report))

print(f"DONE! Scanned {all_files_count} files ({all_code_lines} lines).")
print(f"Summary saved to: {out_summary}")
