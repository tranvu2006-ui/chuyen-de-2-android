# -*- coding: utf-8 -*-
"""
Script dọn sạch dữ liệu thử nghiệm trên Firebase Realtime Database
Chỉ giữ lại tài khoản quản trị viên: admin@gmail.com
"""

import sys
import json
import urllib.request
import urllib.error

# Thiết lập encoding UTF-8 cho console
if sys.platform.startswith('win'):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

API_KEY = "AIzaSyAUV30gmgYxOCs94sp92g5ax27ihwaaFvE"
DATABASE_URL = "https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app"
ADMIN_EMAIL = "admin@gmail.com"
ADMIN_PASSWORD = "123456"

# Danh sách các bảng/node nghiệp vụ cần xóa trắng
NODES_TO_CLEAR = [
    "stores",
    "merchants",
    "drivers",
    "customers",
    "chefs",
    "orders",
    "carts",
    "boss_stores",
    "tracking",
    "order_chats",
    "complaints",
    "dish_reviews",
    "combos",
    "menus",
    "restaurants",
    "customer_addresses",
    "trust_score_logs"
]

def make_request(url, method="GET", data=None):
    headers = {"Content-Type": "application/json"}
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        content = resp.read().decode("utf-8")
        return json.loads(content) if content else None

def main():
    print("=" * 60)
    print("   OISHI FOOD - CÔNG CỤ DỌN SẠCH DỮ LIỆU TEST TRÊN FIREBASE")
    print("=" * 60)

    # 1. Đăng nhập tài khoản admin để lấy ID Token có quyền Admin
    print(f"\n[1/4] Đang đăng nhập xác thực quyền Quản trị viên ({ADMIN_EMAIL})...")
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
    try:
        auth_res = make_request(auth_url, method="POST", data={
            "email": ADMIN_EMAIL,
            "password": ADMIN_PASSWORD,
            "returnSecureToken": True
        })
        id_token = auth_res.get("idToken")
        admin_uid = auth_res.get("localId")
        print(f" -> Đăng nhập thành công! Admin UID: {admin_uid}")
    except Exception as e:
        print(f"❌ Lỗi đăng nhập Admin: {e}")
        return

    # 2. Đọc và sao lưu dữ liệu của tài khoản admin@gmail.com
    print("\n[2/4] Đang đọc và bảo lưu tài khoản Admin...")
    users_url = f"{DATABASE_URL}/users.json?auth={id_token}"
    admin_data = None
    try:
        all_users = make_request(users_url, method="GET") or {}
        admin_data = all_users.get(admin_uid)
        
        # Nếu chưa có profile trong bảng users thì tạo profile mặc định
        if not admin_data:
            admin_data = {
                "uid": admin_uid,
                "email": ADMIN_EMAIL,
                "fullName": "Quản trị viên",
                "name": "Quản trị viên",
                "role": "ADMIN",
                "status": "ACTIVE",
                "phone": "0900000000",
                "createdAt": 1730000000000
            }
        print(f" -> Đã bảo lưu thông tin Admin ({admin_data.get('email')}) an toàn.")
    except Exception as e:
        print(f"❌ Lỗi đọc bảng users: {e}")
        return

    # 3. Dọn sạch các node nghiệp vụ
    print("\n[3/4] Đang xóa các node nghiệp vụ thử nghiệm...")
    for node in NODES_TO_CLEAR:
        node_url = f"{DATABASE_URL}/{node}.json?auth={id_token}"
        try:
            make_request(node_url, method="DELETE")
            print(f"  ✓ Đã dọn sạch: {node}")
        except Exception as e:
            print(f"  ⚠️ Bỏ qua/Lỗi khi xóa {node}: {e}")

    # 4. Reset bảng users chỉ còn lại duy nhất admin@gmail.com
    print("\n[4/4] Đang làm sạch bảng users (chỉ giữ lại admin@gmail.com)...")
    try:
        new_users = {admin_uid: admin_data}
        make_request(users_url, method="PUT", data=new_users)
        print("  ✓ Đã cập nhật bảng users: Chỉ còn tài khoản Admin!")
    except Exception as e:
        print(f"❌ Lỗi cập nhật bảng users: {e}")
        return

    print("\n" + "=" * 60)
    print("🎉 HOÀN TẤT DỌN SẠCH TOÀN BỘ DỮ LIỆU TEST!")
    print("   - Hệ thống đã quay về trạng thái sạch ban đầu.")
    print(f"   - Tài khoản Admin duy nhất được giữ lại: {ADMIN_EMAIL}")
    print("   - Cấu hình hệ thống (danh mục, time frames) được bảo lưu nguyên vẹn.")
    print("=" * 60)

if __name__ == "__main__":
    main()
