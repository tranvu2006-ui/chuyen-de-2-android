import os
import re

modules = ["core", "app-customer", "app-internal"]
results = {
    "models": [],
    "repositories": [],
    "services": [],
    "utils": [],
    "customer_ui": [],
    "internal_ui": [],
    "xml_layouts": [],
    "enums_and_constants": []
}

for mod in modules:
    for root, dirs, files in os.walk(mod):
        # Skip build dirs
        if "build" in root or ".gradle" in root:
            continue
        for f in files:
            path = os.path.join(root, f)
            rel_path = os.path.relpath(path, start=r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android")
            
            if f.endswith(".kt"):
                with open(path, "r", encoding="utf-8", errors="ignore") as file:
                    content = file.read()
                
                # Check package and classes
                classes = re.findall(r"(?:class|object|interface)\s+([A-Za-z0-9_]+)", content)
                methods = re.findall(r"fun\s+([A-Za-z0-9_]+)\s*\(", content)
                constants = re.findall(r"const\s+val\s+([A-Za-z0-9_]+)\s*=\s*([^\n]+)", content)
                
                entry = {
                    "file": f,
                    "path": rel_path,
                    "classes": classes,
                    "methods_count": len(methods),
                    "methods_sample": methods[:10],
                    "constants": constants[:10]
                }
                
                if "model" in root:
                    results["models"].append(entry)
                elif "firebase" in root or "repository" in root.lower():
                    results["repositories"].append(entry)
                elif "service" in root:
                    results["services"].append(entry)
                elif "util" in root:
                    results["utils"].append(entry)
                elif "customer" in root and "ui" in root:
                    results["customer_ui"].append(entry)
                elif "internal" in root and "ui" in root:
                    results["internal_ui"].append(entry)
                    
            elif f.endswith(".xml") and "layout" in root:
                results["xml_layouts"].append(f)

# Also check admin.html
admin_path = r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\admin.html"
admin_funcs = []
if os.path.exists(admin_path):
    with open(admin_path, "r", encoding="utf-8", errors="ignore") as f:
        admin_content = f.read()
    admin_funcs = re.findall(r"function\s+([A-Za-z0-9_]+)\s*\(", admin_content)

print(f"=== TỔNG KẾT QUÁ TRÌNH QUÉT SÂU TOÀN BỘ MÃ NGUỒN ===")
print(f"1. Models: {len(results['models'])} files")
for m in results['models']:
    print(f"   - {m['file']}: {', '.join(m['classes'])}")

print(f"\n2. Repositories: {len(results['repositories'])} files")
for r in results['repositories']:
    print(f"   - {r['file']}: {r['methods_count']} methods")

print(f"\n3. Services: {len(results['services'])} files")
for s in results['services']:
    print(f"   - {s['file']}")

print(f"\n4. Utils: {len(results['utils'])} files")
for u in results['utils']:
    print(f"   - {u['file']}")

print(f"\n5. Customer UI: {len(results['customer_ui'])} files")
print(f"6. Internal UI: {len(results['internal_ui'])} files")
print(f"7. Layout XML: {len(results['xml_layouts'])} layouts")
print(f"8. Web Admin: {len(admin_funcs)} functions")

# Save full inspection report
with open(r"c:\DuAn\Chuyen-De-Tot-Nghiep-2-android\docs\CHI_TIET_TOAN_BO_MA_NGUON_THUC_TE.txt", "w", encoding="utf-8") as out:
    out.write("BẢNG TỔNG HỢP TOÀN BỘ MÃ NGUỒN THỰC TẾ OISHI FOOD\n")
    out.write("="*70 + "\n\n")
    
    out.write("1. DANH SÁCH DATA MODELS (:core/data/model)\n")
    for m in results['models']:
        out.write(f"- {m['path']} (Classes: {', '.join(m['classes'])})\n")
        if m['constants']:
            for c_name, c_val in m['constants']:
                out.write(f"    const {c_name} = {c_val}\n")
    out.write("\n" + "="*70 + "\n\n")

    out.write("2. REPOSITORIES & DATA ACCESS (:core/data/firebase)\n")
    for r in results['repositories']:
        out.write(f"- {r['path']} ({r['methods_count']} methods)\n")
        for meth in r['methods_sample']:
            out.write(f"    fun {meth}()\n")
    out.write("\n" + "="*70 + "\n\n")

    out.write("3. UI CUSTOMER (:app-customer/ui)\n")
    for c in results['customer_ui']:
        out.write(f"- {c['path']} (Classes: {', '.join(c['classes'])})\n")
    out.write("\n" + "="*70 + "\n\n")

    out.write("4. UI INTERNAL (:app-internal/ui)\n")
    for i in results['internal_ui']:
        out.write(f"- {i['path']} (Classes: {', '.join(i['classes'])})\n")
    out.write("\n" + "="*70 + "\n\n")

    out.write("5. SERVICES & BACKGROUND TASKS\n")
    for s in results['services']:
        out.write(f"- {s['path']}\n")
    out.write("\n" + "="*70 + "\n\n")

    out.write("6. WEB ADMIN (admin.html)\n")
    out.write(f"Tổng số hàm JS: {len(admin_funcs)}\n")
    for af in admin_funcs:
        out.write(f"  function {af}()\n")
