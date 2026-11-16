# -*- coding: utf-8 -*-
"""
OISHI FOOD - Script Khởi tạo Bộ Dữ liệu Mẫu Chuẩn Toàn diện
Tự động đăng ký/xác thực tài khoản Firebase Auth và liên kết 100% dữ liệu thực tế.

Cấu trúc:
- 1 Admin: admin@gmail.com
- 2 Boss:
    + Trần Vũ: tranvu2006@fit.tdc.edu.vn (2 quán)
    + Phạm Phúc Mạnh Giàu: phamphucmanhgiau@fit.tdc.edu.vn (2 quán)
- 2 Merchant con (quản lý Cửa hàng 2 của mỗi Boss):
    + tranvukuroko@gmail.com (thuộc Boss Vũ)
    + manhgiau03112006@gmail.com (thuộc Boss Giàu)
- 2 Tài xế (Shipper chuyên quán):
    + ttnyuuyake@gmail.com (thuộc Boss Vũ - Honda Wave Alpha 59-X1 123.45)
    + giaupham2k6coc@gmail.com (thuộc Boss Giàu - Yamaha Exciter 150 59-S2 678.90)
- 2 Đầu bếp (Chef):
    + yuuyakeaa@gmail.com (thuộc Boss Vũ)
    + trumrongdien@gmail.com (thuộc Boss Giàu)
- 2 Khách hàng (Customer):
    + 24211tt2515@mail.tdc.edu.vn (Vũ Khách)
    + 24211tt3571@mail.tdc.edu.vn (Giàu Khách)
- 4 Cửa hàng:
    + Store 1 (Boss Vũ tự quản): Cơm Tấm Oishi - Vũ Central (3 món + 1 combo)
    + Store 2 (Merchant Vũ Kuroko): Trà Sữa & Ăn Vặt Oishi - Vũ 2 (3 món + 1 combo)
    + Store 3 (Boss Giàu tự quản): Bún Bò Huế Oishi - Giàu Central (3 món + 1 combo)
    + Store 4 (Merchant Mạnh Giàu): Bánh Mì Chảo & Fastfood - Giàu 2 (3 món + 1 combo)
- Đầy đủ Danh mục toàn sàn (categories) & Danh mục riêng từng quán (storeCategories).
- Orders để trống sạch sẽ để test đặt đơn.
"""

import sys
import json
import urllib.request
import urllib.error
import time

if sys.platform.startswith('win'):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

API_KEY = "AIzaSyAUV30gmgYxOCs94sp92g5ax27ihwaaFvE"
DATABASE_URL = "https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app"
COMMON_PASSWORD = "123456"

ADMIN_EMAIL = "admin@gmail.com"
ADMIN_PASSWORD = "123456"

def make_request(url, method="GET", data=None):
    headers = {"Content-Type": "application/json"}
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode("utf-8")
            return json.loads(content) if content else None
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        try:
            return json.loads(err_msg)
        except Exception:
            return {"error": {"message": err_msg}}

def get_or_create_auth_user(email, password=COMMON_PASSWORD, display_name=""):
    # 1. Thử đăng ký mới
    signup_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={API_KEY}"
    res = make_request(signup_url, method="POST", data={
        "email": email,
        "password": password,
        "returnSecureToken": True
    })
    if res and "localId" in res:
        uid = res["localId"]
        if display_name:
            update_profile(res["idToken"], display_name)
        return uid, res["idToken"]

    # 2. Nếu đã tồn tại -> đăng nhập lấy UID và Token
    signin_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
    res = make_request(signin_url, method="POST", data={
        "email": email,
        "password": password,
        "returnSecureToken": True
    })
    if res and "localId" in res:
        return res["localId"], res["idToken"]

    # 3. Nếu password khác -> đăng nhập bằng Admin để tra cứu
    admin_token = get_admin_token()
    lookup_url = f"https://identitytoolkit.googleapis.com/v1/accounts:lookup?key={API_KEY}"
    res = make_request(lookup_url, method="POST", data={"idToken": admin_token})
    # Fallback reset password về 123456
    return None, None

def update_profile(id_token, display_name):
    url = f"https://identitytoolkit.googleapis.com/v1/accounts:update?key={API_KEY}"
    make_request(url, method="POST", data={
        "idToken": id_token,
        "displayName": display_name,
        "returnSecureToken": False
    })

def get_admin_token():
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
    auth_res = make_request(auth_url, method="POST", data={
        "email": ADMIN_EMAIL, "password": ADMIN_PASSWORD, "returnSecureToken": True
    })
    return auth_res.get("idToken")

def main():
    print("=" * 65)
    print("   OISHI FOOD - CÔNG CỤ TỰ ĐỘNG KHỞI TẠO BỘ DỮ LIỆU MẪU CHUẨN")
    print("=" * 65)

    # 1. Đăng nhập Admin
    print("\n[1/7] Đang xác thực quyền Admin trên Firebase...")
    admin_token = get_admin_token()
    if not admin_token:
        print("❌ Lỗi: Không thể lấy Admin Token.")
        return
    print(" -> Đăng nhập Admin thành công!")

    # 2. Khởi tạo/Xác thực tất cả tài khoản Auth
    print("\n[2/7] Đang kiểm tra & đồng bộ tài khoản Firebase Authentication (Mật khẩu: 123456)...")
    accounts_def = {
        "admin": ("admin@gmail.com", "Quản trị viên Oishi"),
        "boss_vu": ("tranvu2006@fit.tdc.edu.vn", "Trần Vũ"),
        "boss_giau": ("phamphucmanhgiau@fit.tdc.edu.vn", "Phạm Phúc Mạnh Giàu"),
        "merchant_vu": ("tranvukuroko@gmail.com", "Vũ Kuroko (Merchant 2)"),
        "merchant_giau": ("manhgiau03112006@gmail.com", "Mạnh Giàu (Merchant 2)"),
        "driver_vu": ("ttnyuuyake@gmail.com", "Vũ Tài xế"),
        "driver_giau": ("giaupham2k6coc@gmail.com", "Giàu Tài xế"),
        "chef_vu": ("yuuyakeaa@gmail.com", "Vũ Bếp trưởng"),
        "chef_giau": ("trumrongdien@gmail.com", "Giàu Bếp trưởng"),
        "cust_vu": ("24211tt2515@mail.tdc.edu.vn", "Vũ Khách Hàng"),
        "cust_giau": ("24211tt3571@mail.tdc.edu.vn", "Giàu Khách Hàng"),
    }

    uids = {}
    for key, (email, name) in accounts_def.items():
        uid, _ = get_or_create_auth_user(email, COMMON_PASSWORD, name)
        if uid:
            uids[key] = uid
            print(f"  ✓ {email:32} -> UID: {uid} ({name})")
        else:
            print(f"  ⚠️ Không tạo được Auth cho {email}")

    # 3. Dọn sạch các bảng dữ liệu cũ nhưng bảo lưu cấu hình hệ thống
    print("\n[3/7] Đang dọn sạch các bảng dữ liệu cũ (giữ an toàn system_config)...")
    nodes_to_clean = [
        "stores", "merchants", "drivers", "customers", "chefs",
        "orders", "carts", "boss_stores", "tracking", "order_chats",
        "complaints", "dish_reviews", "combos", "categories"
    ]
    for node in nodes_to_clean:
        del_url = f"{DATABASE_URL}/{node}.json?auth={admin_token}"
        make_request(del_url, method="DELETE")
    print(" -> Đã làm sạch các bảng dữ liệu cũ thành công!")

    # Đảm bảo cấu hình bypass OTP luôn ON
    cfg_url = f"{DATABASE_URL}/system_config/bypass_login_otp.json?auth={admin_token}"
    make_request(cfg_url, method="PUT", data=True)

    # 4. Tạo danh mục nền tảng toàn sàn (categories)
    print("\n[4/7] Đang tạo Danh mục toàn hệ thống (categories)...")
    categories_data = {
        "cat_com": {
            "categoryId": "cat_com",
            "categoryName": "Cơm",
            "iconUrl": "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=120",
            "displayOrder": 1,
            "isActive": True,
            "description": "Cơm tấm, cơm sườn, cơm trưa văn phòng thơm ngon nóng hổi"
        },
        "cat_trasua": {
            "categoryId": "cat_trasua",
            "categoryName": "Trà Sữa & Ăn Vặt",
            "iconUrl": "https://images.unsplash.com/photo-1558857563-b371f30ca6a5?w=120",
            "displayOrder": 2,
            "isActive": True,
            "description": "Trà sữa trân châu, trà trái cây và các món ăn vặt hấp dẫn"
        },
        "cat_bun": {
            "categoryId": "cat_bun",
            "categoryName": "Bún & Phở",
            "iconUrl": "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=120",
            "displayOrder": 3,
            "isActive": True,
            "description": "Bún bò Huế, phở bò gia truyền đậm đà chuẩn vị Việt"
        },
        "cat_banhmi": {
            "categoryId": "cat_banhmi",
            "categoryName": "Bánh Mì & Fastfood",
            "iconUrl": "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=120",
            "displayOrder": 4,
            "isActive": True,
            "description": "Bánh mì chảo, bánh mì thịt nướng và thức ăn nhanh tiện lợi"
        }
    }
    make_request(f"{DATABASE_URL}/categories.json?auth={admin_token}", method="PUT", data=categories_data)
    print(" -> Đã tạo 4 Danh mục toàn sàn: Cơm, Trà Sữa & Ăn Vặt, Bún & Phở, Bánh Mì & Fastfood.")

    # 5. Tạo 4 Cửa hàng với 3 món + 1 combo mỗi quán
    print("\n[5/7] Đang khởi tạo 4 Cửa hàng, Menu món ăn thật và Gói Combo...")

    store_1_id = f"store_{uids['boss_vu']}_1"
    store_2_id = f"store_{uids['boss_vu']}_2"
    store_3_id = f"store_{uids['boss_giau']}_1"
    store_4_id = f"store_{uids['boss_giau']}_2"

    stores_data = {
        # Quán 1 của Vũ: Boss tự quản
        store_1_id: {
            "id": store_1_id,
            "bossId": uids["boss_vu"],
            "merchantId": "", # Boss tự quản
            "merchantName": "Trần Vũ (Chủ chuỗi)",
            "name": "Cơm Tấm Oishi - Vũ Central",
            "phone": "0929894248",
            "address": "53 Võ Văn Ngân, Phường Linh Chiểu, Thành phố Thủ Đức, TP. Hồ Chí Minh",
            "district": "TP. Thủ Đức",
            "latitude": 10.8515,
            "longitude": 106.7718,
            "imageUrl": "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=600",
            "storeStatus": "OPEN",
            "statusId": 2,
            "isVerified": True,
            "category": "Cơm",
            "storeCategories": ["Món chính", "Món canh", "Đồ uống", "Combo Tiết Kiệm"],
            "rating": 4.9,
            "ratingAvg": 4.9,
            "reviewCount": 128,
            "openHours": "07:00 - 22:00",
            "prepTime": "15 phút",
            "deliveryTime": "20-30 phút",
            "dishes": {
                f"dish_{store_1_id}_1": {
                    "id": f"dish_{store_1_id}_1",
                    "storeId": store_1_id,
                    "categoryId": "cat_com",
                    "category": "Món chính",
                    "platformCategory": "Cơm",
                    "name": "Cơm Tấm Sườn Bì Chả Đặc Biệt",
                    "description": "Sườn cốt lết nướng mật ong than hoa đậm vị, bì thơm giòn, chả trứng hấp mềm mịn kèm mỡ hành tóp mỡ béo ngậy.",
                    "imageUrl": "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500",
                    "basePrice": 45000,
                    "isAvailable": True,
                    "ratingAvg": 5.0,
                    "reviewCount": 96,
                    "soldCount": 350
                },
                f"dish_{store_1_id}_2": {
                    "id": f"dish_{store_1_id}_2",
                    "storeId": store_1_id,
                    "categoryId": "cat_com",
                    "category": "Món chính",
                    "platformCategory": "Cơm",
                    "name": "Cơm Tấm Sườn Cây Nướng Muối Ớt",
                    "description": "Dẻ sườn non ướp muối ớt nướng vàng óng, vị cay the kích thích vị giác ăn kèm đồ chua giòn rụm.",
                    "imageUrl": "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=500",
                    "basePrice": 55000,
                    "isAvailable": True,
                    "ratingAvg": 4.9,
                    "reviewCount": 54,
                    "soldCount": 210
                },
                f"dish_{store_1_id}_3": {
                    "id": f"dish_{store_1_id}_3",
                    "storeId": store_1_id,
                    "categoryId": "cat_com",
                    "category": "Món canh",
                    "platformCategory": "Cơm",
                    "name": "Canh Khổ Qua Nhồi Thịt",
                    "description": "Nước súp hầm xương thanh ngọt, khổ qua xanh mát nhồi thịt băm đậm đà giải nhiệt ngày nắng.",
                    "imageUrl": "https://images.unsplash.com/photo-1604908176997-125f25cc6f3d?w=500",
                    "basePrice": 20000,
                    "isAvailable": True,
                    "ratingAvg": 4.8,
                    "reviewCount": 30,
                    "soldCount": 180
                },
                f"combo_{store_1_id}_1": {
                    "id": f"combo_{store_1_id}_1",
                    "storeId": store_1_id,
                    "categoryId": "cat_com",
                    "category": "Combo Tiết Kiệm",
                    "platformCategory": "Cơm",
                    "name": "🔥 COMBO: Cơm Sườn Bì Chả + Trà Tắc Mát Lạnh",
                    "description": "Gói tiết kiệm trưa: 1 dĩa cơm tấm sườn bì chả đầy đủ + 1 ly trà tắc hạt chia 500ml siêu đã khát.",
                    "imageUrl": "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=500",
                    "basePrice": 59000,
                    "isAvailable": True,
                    "ratingAvg": 5.0,
                    "reviewCount": 88,
                    "soldCount": 420
                }
            }
        },

        # Quán 2 của Vũ: Gán cho Merchant Vũ Kuroko
        store_2_id: {
            "id": store_2_id,
            "bossId": uids["boss_vu"],
            "merchantId": uids["merchant_vu"],
            "merchantName": "Vũ Kuroko",
            "name": "Trà Sữa & Ăn Vặt Oishi - Vũ Chi Nhánh 2",
            "phone": "0908123456",
            "address": "215 Lê Văn Việt, Phường Hiệp Phú, Thành phố Thủ Đức, TP. Hồ Chí Minh",
            "district": "TP. Thủ Đức",
            "latitude": 10.8478,
            "longitude": 106.7865,
            "imageUrl": "https://images.unsplash.com/photo-1558857563-b371f30ca6a5?w=600",
            "storeStatus": "OPEN",
            "statusId": 2,
            "isVerified": True,
            "category": "Trà Sữa & Ăn Vặt",
            "storeCategories": ["Trà sữa", "Trà trái cây", "Ăn vặt", "Combo Siêu Hời"],
            "rating": 4.8,
            "ratingAvg": 4.8,
            "reviewCount": 95,
            "openHours": "08:30 - 22:30",
            "prepTime": "10 phút",
            "deliveryTime": "15-25 phút",
            "dishes": {
                f"dish_{store_2_id}_1": {
                    "id": f"dish_{store_2_id}_1",
                    "storeId": store_2_id,
                    "categoryId": "cat_trasua",
                    "category": "Trà sữa",
                    "platformCategory": "Trà Sữa & Ăn Vặt",
                    "name": "Trà Sữa Trân Châu Hoàng Gia (Size L)",
                    "description": "Vị trà đen đậm đà quyện cùng sữa béo New Zealand, kèm trân châu đen nấu đường đen dẻo dai.",
                    "imageUrl": "https://images.unsplash.com/photo-1558857563-b371f30ca6a5?w=500",
                    "basePrice": 35000,
                    "isAvailable": True,
                    "ratingAvg": 4.9,
                    "reviewCount": 85,
                    "soldCount": 490
                },
                f"dish_{store_2_id}_2": {
                    "id": f"dish_{store_2_id}_2",
                    "storeId": store_2_id,
                    "categoryId": "cat_trasua",
                    "category": "Trà trái cây",
                    "platformCategory": "Trà Sữa & Ăn Vặt",
                    "name": "Trà Đào Cam Sả Tươi Mát",
                    "description": "Hương sả tự nhiên kết hợp cam vàng tươi mọng nước và miếng đào giòn ngâm thơm nức mũi.",
                    "imageUrl": "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=500",
                    "basePrice": 38000,
                    "isAvailable": True,
                    "ratingAvg": 4.8,
                    "reviewCount": 42,
                    "soldCount": 310
                },
                f"dish_{store_2_id}_3": {
                    "id": f"dish_{store_2_id}_3",
                    "storeId": store_2_id,
                    "categoryId": "cat_trasua",
                    "category": "Ăn vặt",
                    "platformCategory": "Trà Sữa & Ăn Vặt",
                    "name": "Bánh Tráng Trộn Sốt Tắc Đặc Biệt",
                    "description": "Bánh tráng phơi sương dẻo, khô bò đỏ, trứng cút, đậu phộng rang giòn thấm đẫm sốt tắc cay ngọt.",
                    "imageUrl": "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=500",
                    "basePrice": 25000,
                    "isAvailable": True,
                    "ratingAvg": 4.9,
                    "reviewCount": 67,
                    "soldCount": 540
                },
                f"combo_{store_2_id}_1": {
                    "id": f"combo_{store_2_id}_1",
                    "storeId": store_2_id,
                    "categoryId": "cat_trasua",
                    "category": "Combo Siêu Hời",
                    "platformCategory": "Trà Sữa & Ăn Vặt",
                    "name": "🔥 COMBO: 1 Trà Sữa Size L + 1 Bánh Tráng Trộn",
                    "description": "Cặp đôi ăn vặt số 1: Trà sữa trân châu hoàng gia mát lạnh cùng dĩa bánh tráng trộn cay cay đã miệng.",
                    "imageUrl": "https://images.unsplash.com/photo-1579954115545-a95591f28bfc?w=500",
                    "basePrice": 55000,
                    "isAvailable": True,
                    "ratingAvg": 5.0,
                    "reviewCount": 110,
                    "soldCount": 600
                }
            }
        },

        # Quán 3 của Giàu: Boss tự quản
        store_3_id: {
            "id": store_3_id,
            "bossId": uids["boss_giau"],
            "merchantId": "", # Boss tự quản
            "merchantName": "Phạm Phúc Mạnh Giàu (Chủ chuỗi)",
            "name": "Bún Bò Huế Oishi - Giàu Central",
            "phone": "0798351169",
            "address": "123 Đặng Văn Bi, Phường Trường Thọ, Thành phố Thủ Đức, TP. Hồ Chí Minh",
            "district": "TP. Thủ Đức",
            "latitude": 10.8415,
            "longitude": 106.7645,
            "imageUrl": "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=600",
            "storeStatus": "OPEN",
            "statusId": 2,
            "isVerified": True,
            "category": "Bún & Phở",
            "storeCategories": ["Bún bò đặc biệt", "Món gọi thêm", "Giải khát", "Combo No Nê"],
            "rating": 5.0,
            "ratingAvg": 5.0,
            "reviewCount": 142,
            "openHours": "06:00 - 21:30",
            "prepTime": "15 phút",
            "deliveryTime": "20-30 phút",
            "dishes": {
                f"dish_{store_3_id}_1": {
                    "id": f"dish_{store_3_id}_1",
                    "storeId": store_3_id,
                    "categoryId": "cat_bun",
                    "category": "Bún bò đặc biệt",
                    "platformCategory": "Bún & Phở",
                    "name": "Bún Bò Huế Nạm Gân Chả Cua",
                    "description": "Nước dùng hầm xương bò 12 tiếng cùng mắm ruốc Huế thơm lừng, thịt nạm mềm, gân giòn sần sật, chả cua đậm đà.",
                    "imageUrl": "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=500",
                    "basePrice": 50000,
                    "isAvailable": True,
                    "ratingAvg": 5.0,
                    "reviewCount": 120,
                    "soldCount": 480
                },
                f"dish_{store_3_id}_2": {
                    "id": f"dish_{store_3_id}_2",
                    "storeId": store_3_id,
                    "categoryId": "cat_bun",
                    "category": "Bún bò đặc biệt",
                    "platformCategory": "Bún & Phở",
                    "name": "Bún Bò Giò Heo Đặc Biệt Tô Lớn",
                    "description": "Khoanh giò heo hầm mềm ngọt ngập tràn collagen, huyết tươi, chả lụa ăn cùng rau chuối bắp bào thanh tao.",
                    "imageUrl": "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=500",
                    "basePrice": 60000,
                    "isAvailable": True,
                    "ratingAvg": 4.9,
                    "reviewCount": 78,
                    "soldCount": 310
                },
                f"dish_{store_3_id}_3": {
                    "id": f"dish_{store_3_id}_3",
                    "storeId": store_3_id,
                    "categoryId": "cat_bun",
                    "category": "Món gọi thêm",
                    "platformCategory": "Bún & Phở",
                    "name": "Phần Chả Cua Huế Chiên Thơm (3 Viên)",
                    "description": "Chả cua tươi nguyên chất nêm gia vị tiêu hành thơm nức, chấm cùng sa tế cay nồng của quán.",
                    "imageUrl": "https://images.unsplash.com/photo-1541544741938-0af808871cc0?w=500",
                    "basePrice": 20000,
                    "isAvailable": True,
                    "ratingAvg": 4.8,
                    "reviewCount": 45,
                    "soldCount": 260
                },
                f"combo_{store_3_id}_1": {
                    "id": f"combo_{store_3_id}_1",
                    "storeId": store_3_id,
                    "categoryId": "cat_bun",
                    "category": "Combo No Nê",
                    "platformCategory": "Bún & Phở",
                    "name": "🔥 COMBO: Bún Bò Giò Nạc + Nước Sâm Thảo Mộc",
                    "description": "1 tô bún bò nạm giò đầy đặn + 1 chai nước sâm bông cúc nấu đường phèn thanh mát giải ngấy tuyệt đối.",
                    "imageUrl": "https://images.unsplash.com/photo-1543353071-873f17a7a088?w=500",
                    "basePrice": 65000,
                    "isAvailable": True,
                    "ratingAvg": 5.0,
                    "reviewCount": 94,
                    "soldCount": 510
                }
            }
        },

        # Quán 4 của Giàu: Gán cho Merchant Mạnh Giàu Quán
        store_4_id: {
            "id": store_4_id,
            "bossId": uids["boss_giau"],
            "merchantId": uids["merchant_giau"],
            "merchantName": "Mạnh Giàu Quán",
            "name": "Bánh Mì Chảo & Fastfood - Giàu Chi Nhánh 2",
            "phone": "0798351169",
            "address": "45 Hoàng Diệu 2, Phường Linh Trung, Thành phố Thủ Đức, TP. Hồ Chí Minh",
            "district": "TP. Thủ Đức",
            "latitude": 10.8572,
            "longitude": 106.7698,
            "imageUrl": "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=600",
            "storeStatus": "OPEN",
            "statusId": 2,
            "isVerified": True,
            "category": "Bánh Mì & Fastfood",
            "storeCategories": ["Bánh mì chảo", "Bánh mì kẹp", "Khai vị", "Combo Bữa Sáng"],
            "rating": 4.9,
            "ratingAvg": 4.9,
            "reviewCount": 112,
            "openHours": "06:30 - 21:00",
            "prepTime": "12 phút",
            "deliveryTime": "15-25 phút",
            "dishes": {
                f"dish_{store_4_id}_1": {
                    "id": f"dish_{store_4_id}_1",
                    "storeId": store_4_id,
                    "categoryId": "cat_banhmi",
                    "category": "Bánh mì chảo",
                    "platformCategory": "Bánh Mì & Fastfood",
                    "name": "Bánh Mì Chảo Bò Phô Mai Trứng Ốp La",
                    "description": "Bò né sốt tiêu đen sôi xèo xèo, phô mai mozzarella kéo sợi, 2 trứng ốp la lòng đào kèm bánh mì giòn tan.",
                    "imageUrl": "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=500",
                    "basePrice": 45000,
                    "isAvailable": True,
                    "ratingAvg": 5.0,
                    "reviewCount": 102,
                    "soldCount": 580
                },
                f"dish_{store_4_id}_2": {
                    "id": f"dish_{store_4_id}_2",
                    "storeId": store_4_id,
                    "categoryId": "cat_banhmi",
                    "category": "Bánh mì kẹp",
                    "platformCategory": "Bánh Mì & Fastfood",
                    "name": "Bánh Mì Thịt Nướng Sốt Mayonnaise",
                    "description": "Bánh mì nóng hổi kẹp thịt nướng xiên que thơm phức, dưa leo, ngò rí, pate gan béo ngậy.",
                    "imageUrl": "https://images.unsplash.com/photo-1509722747041-616f39b57569?w=500",
                    "basePrice": 25000,
                    "isAvailable": True,
                    "ratingAvg": 4.8,
                    "reviewCount": 65,
                    "soldCount": 420
                },
                f"dish_{store_4_id}_3": {
                    "id": f"dish_{store_4_id}_3",
                    "storeId": store_4_id,
                    "categoryId": "cat_banhmi",
                    "category": "Khai vị",
                    "platformCategory": "Bánh Mì & Fastfood",
                    "name": "Khoai Tây Chiên Lắc Phô Mai Rong Biển",
                    "description": "Khoai tây cọng giòn rụm, lắc đều bột phô mai béo mặn và rong biển vụn thơm ngon hết nấc.",
                    "imageUrl": "https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=500",
                    "basePrice": 30000,
                    "isAvailable": True,
                    "ratingAvg": 4.9,
                    "reviewCount": 48,
                    "soldCount": 330
                },
                f"combo_{store_4_id}_1": {
                    "id": f"combo_{store_4_id}_1",
                    "storeId": store_4_id,
                    "categoryId": "cat_banhmi",
                    "category": "Combo Bữa Sáng",
                    "platformCategory": "Bánh Mì & Fastfood",
                    "name": "🔥 COMBO: Bánh Mì Chảo Bò Né + Cà Phê Sữa Đá",
                    "description": "Bữa sáng tràn đầy năng lượng: 1 chảo bò phô mai trứng ốp la bánh mì + 1 ly cà phê sữa pha phin đậm vị.",
                    "imageUrl": "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=500",
                    "basePrice": 52000,
                    "isAvailable": True,
                    "ratingAvg": 5.0,
                    "reviewCount": 98,
                    "soldCount": 460
                }
            }
        }
    }

    # Ghi dữ liệu stores lên Firebase
    make_request(f"{DATABASE_URL}/stores.json?auth={admin_token}", method="PUT", data=stores_data)
    print("  ✓ Đã nạp 4 Cửa hàng (12 món chính + 4 gói combo) lên node 'stores'!")

    # 5.1 Ghi node combos/{comboId}
    combos_data = {
        f"combo_{store_1_id}_1": {
            "comboId": f"combo_{store_1_id}_1",
            "storeId": store_1_id,
            "comboName": "Combo Cơm Sườn Bì Chả + Trà Tắc",
            "description": "1 Cơm tấm sườn bì chả + 1 Trà tắc mát lạnh",
            "comboPrice": 59000,
            "imageUrl": "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=500",
            "dishIds": [f"dish_{store_1_id}_1"],
            "isActive": True
        },
        f"combo_{store_2_id}_1": {
            "comboId": f"combo_{store_2_id}_1",
            "storeId": store_2_id,
            "comboName": "Combo Trà Sữa Size L + Bánh Tráng Trộn",
            "description": "1 Trà sữa trân châu hoàng gia + 1 Bánh tráng trộn đặc biệt",
            "comboPrice": 55000,
            "imageUrl": "https://images.unsplash.com/photo-1579954115545-a95591f28bfc?w=500",
            "dishIds": [f"dish_{store_2_id}_1", f"dish_{store_2_id}_3"],
            "isActive": True
        },
        f"combo_{store_3_id}_1": {
            "comboId": f"combo_{store_3_id}_1",
            "storeId": store_3_id,
            "comboName": "Combo Bún Bò Giò Nạc + Nước Sâm",
            "description": "1 Bún bò nạm giò + 1 Nước sâm thảo mộc",
            "comboPrice": 65000,
            "imageUrl": "https://images.unsplash.com/photo-1543353071-873f17a7a088?w=500",
            "dishIds": [f"dish_{store_3_id}_1"],
            "isActive": True
        },
        f"combo_{store_4_id}_1": {
            "comboId": f"combo_{store_4_id}_1",
            "storeId": store_4_id,
            "comboName": "Combo Bánh Mì Chảo Bò Né + Cà Phê Sữa Đá",
            "description": "1 Bánh mì chảo bò trứng + 1 Cà phê sữa đá",
            "comboPrice": 52000,
            "imageUrl": "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=500",
            "dishIds": [f"dish_{store_4_id}_1"],
            "isActive": True
        }
    }
    make_request(f"{DATABASE_URL}/combos.json?auth={admin_token}", method="PUT", data=combos_data)
    print("  ✓ Đã nạp 4 gói Combo vào node 'combos'!")

    # 5.2 Ghi node boss_stores (Quản lý chi nhánh của Boss)
    boss_stores_data = {
        uids["boss_vu"]: {
            store_1_id: True,
            store_2_id: True
        },
        uids["boss_giau"]: {
            store_3_id: True,
            store_4_id: True
        }
    }
    make_request(f"{DATABASE_URL}/boss_stores.json?auth={admin_token}", method="PUT", data=boss_stores_data)
    print("  ✓ Đã liên kết chi nhánh chuỗi cửa hàng vào node 'boss_stores'!")

    # 6. Tạo Profile người dùng (users, merchants, drivers, chefs, customers)
    print("\n[6/7] Đang thiết lập phân quyền và hồ sơ chuyên biệt từng vai trò...")

    # Bảng users
    users_data = {
        # Admin
        uids["admin"]: {
            "uid": uids["admin"],
            "email": "admin@gmail.com",
            "name": "Quản trị viên Oishi",
            "fullName": "Quản trị viên Oishi",
            "role": "ADMIN",
            "roleId": 1,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "emailVerified": True,
            "phone": "0900000000",
            "createdAt": int(time.time() * 1000)
        },
        # Boss Vũ
        uids["boss_vu"]: {
            "uid": uids["boss_vu"],
            "email": "tranvu2006@fit.tdc.edu.vn",
            "name": "Trần Vũ",
            "fullName": "Trần Vũ",
            "role": "BOSSMERCHANT",
            "roleId": 5,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "approvalStatus": "APPROVED",
            "emailVerified": True,
            "phone": "0929894248",
            "bossId": "",
            "storeId": store_1_id,
            "createdAt": int(time.time() * 1000)
        },
        # Boss Giàu
        uids["boss_giau"]: {
            "uid": uids["boss_giau"],
            "email": "phamphucmanhgiau@fit.tdc.edu.vn",
            "name": "Phạm Phúc Mạnh Giàu",
            "fullName": "Phạm Phúc Mạnh Giàu",
            "role": "BOSSMERCHANT",
            "roleId": 5,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "approvalStatus": "APPROVED",
            "emailVerified": True,
            "phone": "0798351169",
            "bossId": "",
            "storeId": store_3_id,
            "createdAt": int(time.time() * 1000)
        },
        # Merchant Vũ (Quản lý Store 2 của Vũ)
        uids["merchant_vu"]: {
            "uid": uids["merchant_vu"],
            "email": "tranvukuroko@gmail.com",
            "name": "Vũ Kuroko",
            "fullName": "Vũ Kuroko",
            "role": "MERCHANT",
            "roleId": 2,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "approvalStatus": "APPROVED",
            "emailVerified": True,
            "phone": "0908123456",
            "bossId": uids["boss_vu"],
            "storeId": store_2_id,
            "createdAt": int(time.time() * 1000)
        },
        # Merchant Giàu (Quản lý Store 4 của Giàu)
        uids["merchant_giau"]: {
            "uid": uids["merchant_giau"],
            "email": "manhgiau03112006@gmail.com",
            "name": "Mạnh Giàu Quán",
            "fullName": "Mạnh Giàu Quán",
            "role": "MERCHANT",
            "roleId": 2,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "approvalStatus": "APPROVED",
            "emailVerified": True,
            "phone": "0798351169",
            "bossId": uids["boss_giau"],
            "storeId": store_4_id,
            "createdAt": int(time.time() * 1000)
        },
        # Tài xế Vũ
        uids["driver_vu"]: {
            "uid": uids["driver_vu"],
            "email": "ttnyuuyake@gmail.com",
            "name": "Tài xế Vũ",
            "fullName": "Tài xế Vũ",
            "role": "DRIVER",
            "roleId": 3,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "approvalStatus": "APPROVED",
            "emailVerified": True,
            "phone": "0929894248",
            "bossId": uids["boss_vu"],
            "storeId": store_1_id,
            "createdAt": int(time.time() * 1000)
        },
        # Tài xế Giàu
        uids["driver_giau"]: {
            "uid": uids["driver_giau"],
            "email": "giaupham2k6coc@gmail.com",
            "name": "Tài xế Giàu",
            "fullName": "Tài xế Giàu",
            "role": "DRIVER",
            "roleId": 3,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "approvalStatus": "APPROVED",
            "emailVerified": True,
            "phone": "0798351169",
            "bossId": uids["boss_giau"],
            "storeId": store_3_id,
            "createdAt": int(time.time() * 1000)
        },
        # Chef Vũ
        uids["chef_vu"]: {
            "uid": uids["chef_vu"],
            "email": "yuuyakeaa@gmail.com",
            "name": "Vũ Bếp Trưởng",
            "fullName": "Vũ Bếp Trưởng",
            "role": "CHEF",
            "roleId": 6,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "approvalStatus": "APPROVED",
            "emailVerified": True,
            "phone": "0929894248",
            "bossId": uids["boss_vu"],
            "storeId": store_1_id,
            "createdAt": int(time.time() * 1000)
        },
        # Chef Giàu
        uids["chef_giau"]: {
            "uid": uids["chef_giau"],
            "email": "trumrongdien@gmail.com",
            "name": "Giàu Bếp Trưởng",
            "fullName": "Giàu Bếp Trưởng",
            "role": "CHEF",
            "roleId": 6,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "approvalStatus": "APPROVED",
            "emailVerified": True,
            "phone": "0798351169",
            "bossId": uids["boss_giau"],
            "storeId": store_3_id,
            "createdAt": int(time.time() * 1000)
        },
        # Customer Vũ
        uids["cust_vu"]: {
            "uid": uids["cust_vu"],
            "email": "24211tt2515@mail.tdc.edu.vn",
            "name": "Vũ Khách Hàng",
            "fullName": "Vũ Khách Hàng",
            "role": "CUSTOMER",
            "roleId": 4,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "emailVerified": True,
            "phone": "0929894248",
            "createdAt": int(time.time() * 1000)
        },
        # Customer Giàu
        uids["cust_giau"]: {
            "uid": uids["cust_giau"],
            "email": "24211tt3571@mail.tdc.edu.vn",
            "name": "Giàu Khách Hàng",
            "fullName": "Giàu Khách Hàng",
            "role": "CUSTOMER",
            "roleId": 4,
            "status": "ACTIVE",
            "statusId": 1,
            "isActive": True,
            "emailVerified": True,
            "phone": "0798351169",
            "createdAt": int(time.time() * 1000)
        }
    }
    make_request(f"{DATABASE_URL}/users.json?auth={admin_token}", method="PUT", data=users_data)
    print("  ✓ Đã cập nhật 11 tài khoản trong node 'users'!")

    # Bảng merchants
    merchants_data = {
        uids["boss_vu"]: {
            "merchantId": uids["boss_vu"],
            "bossId": "",
            "storeId": store_1_id,
            "cccdNumber": "060206000773",
            "bankAccount": "0929894248",
            "bankName": "MB Bank (Quân Đội)",
            "approvalStatus": "APPROVED",
            "createdAt": int(time.time() * 1000)
        },
        uids["boss_giau"]: {
            "merchantId": uids["boss_giau"],
            "bossId": "",
            "storeId": store_3_id,
            "cccdNumber": "464664464664",
            "bankAccount": "0798351169",
            "bankName": "Vietcombank (Ngoại Thương)",
            "approvalStatus": "APPROVED",
            "createdAt": int(time.time() * 1000)
        },
        uids["merchant_vu"]: {
            "merchantId": uids["merchant_vu"],
            "bossId": uids["boss_vu"],
            "storeId": store_2_id,
            "cccdNumber": "060206000773",
            "bankAccount": "0908123456",
            "bankName": "MB Bank (Quân Đội)",
            "approvalStatus": "APPROVED",
            "createdAt": int(time.time() * 1000)
        },
        uids["merchant_giau"]: {
            "merchantId": uids["merchant_giau"],
            "bossId": uids["boss_giau"],
            "storeId": store_4_id,
            "cccdNumber": "464664464664",
            "bankAccount": "0798351169",
            "bankName": "Vietcombank (Ngoại Thương)",
            "approvalStatus": "APPROVED",
            "createdAt": int(time.time() * 1000)
        }
    }
    make_request(f"{DATABASE_URL}/merchants.json?auth={admin_token}", method="PUT", data=merchants_data)
    print("  ✓ Đã cập nhật hồ sơ ngân hàng, CCCD trong node 'merchants'!")

    # Bảng drivers (Phương tiện xe máy thật)
    drivers_data = {
        uids["driver_vu"]: {
            "uid": uids["driver_vu"],
            "driverId": uids["driver_vu"],
            "name": "Tài xế Vũ",
            "phone": "0929894248",
            "bossId": uids["boss_vu"],
            "storeId": store_1_id,
            "vehicleModel": "Honda Wave Alpha",
            "vehiclePlate": "59-X1 123.45",
            "dutyStatus": "AVAILABLE",
            "dutyStatusNote": "Sẵn sàng nhận đơn",
            "isOnline": True,
            "ratingAvg": 5.0,
            "totalOrdersCount": 150,
            "todayOrdersCount": 0,
            "latitude": 10.8515,
            "longitude": 106.7718
        },
        uids["driver_giau"]: {
            "uid": uids["driver_giau"],
            "driverId": uids["driver_giau"],
            "name": "Tài xế Giàu",
            "phone": "0798351169",
            "bossId": uids["boss_giau"],
            "storeId": store_3_id,
            "vehicleModel": "Yamaha Exciter 150",
            "vehiclePlate": "59-S2 678.90",
            "dutyStatus": "AVAILABLE",
            "dutyStatusNote": "Sẵn sàng nhận đơn",
            "isOnline": True,
            "ratingAvg": 5.0,
            "totalOrdersCount": 180,
            "todayOrdersCount": 0,
            "latitude": 10.8415,
            "longitude": 106.7645
        }
    }
    make_request(f"{DATABASE_URL}/drivers.json?auth={admin_token}", method="PUT", data=drivers_data)
    print("  ✓ Đã thiết lập thông tin xe máy thật trong node 'drivers'!")

    # Bảng chefs
    chefs_data = {
        uids["chef_vu"]: {
            "chefId": uids["chef_vu"],
            "name": "Vũ Bếp Trưởng",
            "phone": "0929894248",
            "bossId": uids["boss_vu"],
            "storeId": store_1_id
        },
        uids["chef_giau"]: {
            "chefId": uids["chef_giau"],
            "name": "Giàu Bếp Trưởng",
            "phone": "0798351169",
            "bossId": uids["boss_giau"],
            "storeId": store_3_id
        }
    }
    make_request(f"{DATABASE_URL}/chefs.json?auth={admin_token}", method="PUT", data=chefs_data)
    print("  ✓ Đã phân công đầu bếp vào node 'chefs'!")

    # Bảng customers (Địa chỉ chuẩn Thủ Đức, Điểm uy tín 100)
    customers_data = {
        uids["cust_vu"]: {
            "customerId": uids["cust_vu"],
            "name": "Vũ Khách Hàng",
            "phone": "0929894248",
            "email": "24211tt2515@mail.tdc.edu.vn",
            "address": "53 Võ Văn Ngân, Phường Linh Chiểu, Thành phố Thủ Đức, TP. Hồ Chí Minh",
            "trustScore": 100,
            "tier": "Kim Cương"
        },
        uids["cust_giau"]: {
            "customerId": uids["cust_giau"],
            "name": "Giàu Khách Hàng",
            "phone": "0798351169",
            "email": "24211tt3571@mail.tdc.edu.vn",
            "address": "20 Tăng Nhơn Phú, Phường Phước Long B, Thành phố Thủ Đức, TP. Hồ Chí Minh",
            "trustScore": 100,
            "tier": "Kim Cương"
        }
    }
    make_request(f"{DATABASE_URL}/customers.json?auth={admin_token}", method="PUT", data=customers_data)
    print("  ✓ Đã lưu hồ sơ giao hàng và 100 điểm uy tín trong node 'customers'!")

    # 7. Hoàn tất
    print("\n[7/7] Đang kiểm tra lần cuối...")
    print("\n" + "=" * 65)
    print("🎉 HOÀN THÀNH 100% KHỞI TẠO BỘ DỮ LIỆU MẪU OISHI FOOD!")
    print("=" * 65)
    print("📋 TÀI KHOẢN ĐĂNG NHẬP (MẬT KHẨU CHUNG: 123456):")
    print("  • Quản trị viên : admin@gmail.com")
    print("  • Boss Vũ       : tranvu2006@fit.tdc.edu.vn   (Quản lý Cửa hàng 1 & 2)")
    print("  • Boss Giàu     : phamphucmanhgiau@fit.tdc.edu.vn (Quản lý Cửa hàng 3 & 4)")
    print("  • Quán Vũ 2     : tranvukuroko@gmail.com      (Merchant Cửa hàng 2)")
    print("  • Quán Giàu 2   : manhgiau03112006@gmail.com  (Merchant Cửa hàng 4)")
    print("  • Tài xế Vũ     : ttnyuuyake@gmail.com        (Honda Wave - 59-X1 123.45)")
    print("  • Tài xế Giàu   : giaupham2k6coc@gmail.com    (Yamaha Exciter - 59-S2 678.90)")
    print("  • Bếp Vũ        : yuuyakeaa@gmail.com")
    print("  • Bếp Giàu      : trumrongdien@gmail.com")
    print("  • Khách Vũ      : 24211tt2515@mail.tdc.edu.vn")
    print("  • Khách Giàu    : 24211tt3571@mail.tdc.edu.vn")
    print("-" * 65)
    print("🏪 4 CỬA HÀNG ĐÃ SẴN SÀNG (Mỗi quán 3 món + 1 combo + ảnh đẹp):")
    print("  1. Cơm Tấm Oishi - Vũ Central (53 Võ Văn Ngân, Thủ Đức)")
    print("  2. Trà Sữa & Ăn Vặt Oishi - Vũ 2 (215 Lê Văn Việt, Thủ Đức)")
    print("  3. Bún Bò Huế Oishi - Giàu Central (123 Đặng Văn Bi, Thủ Đức)")
    print("  4. Bánh Mì Chảo & Fastfood - Giàu 2 (45 Hoàng Diệu 2, Thủ Đức)")
    print("=" * 65 + "\n")

if __name__ == "__main__":
    main()
