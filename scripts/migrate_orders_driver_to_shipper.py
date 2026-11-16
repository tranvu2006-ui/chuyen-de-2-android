# -*- coding: utf-8 -*-
"""
Script chuẩn hóa toàn bộ node 'orders' trên Firebase Realtime Database:
- Chuyển toàn bộ key 'driverId' sang 'shipperId'
- Xóa bỏ hoàn toàn key 'driverId' dư thừa trên từng đơn hàng
"""

import sys
import json
import urllib.request
import urllib.error

if sys.platform.startswith('win'):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

API_KEY = "AIzaSyAUV30gmgYxOCs94sp92g5ax27ihwaaFvE"
DATABASE_URL = "https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app"
ADMIN_EMAIL = "admin@gmail.com"
ADMIN_PASSWORD = "123456"

def make_request(url, method="GET", data=None):
    headers = {"Content-Type": "application/json"}
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        content = resp.read().decode("utf-8")
        return json.loads(content) if content else None

def main():
    print("=" * 65)
    print("   CHUẨN HÓA DỮ LIỆU ĐƠN HÀNG: driverId -> shipperId TRÊN FIREBASE")
    print("=" * 65)

    print(f"\n[1/3] Đang đăng nhập quyền Admin ({ADMIN_EMAIL})...")
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
    try:
        auth_res = make_request(auth_url, method="POST", data={
            "email": ADMIN_EMAIL,
            "password": ADMIN_PASSWORD,
            "returnSecureToken": True
        })
        id_token = auth_res.get("idToken")
        print(" -> Đăng nhập thành công!")
    except Exception as e:
        print(f"❌ Lỗi đăng nhập Admin: {e}")
        return

    print("\n[2/3] Đang tải danh sách toàn bộ đơn hàng từ node 'orders'...")
    orders_url = f"{DATABASE_URL}/orders.json?auth={id_token}"
    try:
        all_orders = make_request(orders_url, method="GET") or {}
    except Exception as e:
        print(f"❌ Lỗi tải dữ liệu orders: {e}")
        return

    total_orders = len(all_orders)
    print(f" -> Tổng số đơn hàng trên hệ thống: {total_orders}")

    print("\n[3/3] Đang quét và chuẩn hóa các đơn có 'driverId'...")
    migrated_count = 0
    cleaned_count = 0

    for order_id, order_data in all_orders.items():
        if not isinstance(order_data, dict):
            continue

        has_driver_id = "driverId" in order_data
        driver_id_val = order_data.get("driverId")
        shipper_id_val = order_data.get("shipperId")

        # Cần chuẩn hóa nếu có driverId
        if has_driver_id:
            # 1. Đảm bảo shipperId có giá trị hợp lệ nếu driverId tồn tại
            target_shipper_id = shipper_id_val or driver_id_val
            if target_shipper_id and target_shipper_id != shipper_id_val:
                patch_url = f"{DATABASE_URL}/orders/{order_id}.json?auth={id_token}"
                make_request(patch_url, method="PATCH", data={"shipperId": target_shipper_id})
                migrated_count += 1

            # 2. Xóa bỏ trường driverId khỏi đơn hàng
            delete_driver_id_url = f"{DATABASE_URL}/orders/{order_id}/driverId.json?auth={id_token}"
            make_request(delete_driver_id_url, method="DELETE")
            cleaned_count += 1
            print(f" -> Đã xóa 'driverId' khỏi đơn #{order_id[:8]} (shipperId={target_shipper_id})")

    print("\n" + "=" * 65)
    print(f"   KẾT QUẢ: Đã chuẩn hóa {cleaned_count} đơn hàng có key 'driverId'!")
    print(f"   - Số đơn được bù 'shipperId' từ 'driverId': {migrated_count}")
    print(f"   - Số đơn đã xóa bỏ vĩnh viễn key 'driverId': {cleaned_count}")
    print("=" * 65)

if __name__ == "__main__":
    main()
