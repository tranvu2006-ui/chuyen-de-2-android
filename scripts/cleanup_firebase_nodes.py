# -*- coding: utf-8 -*-
"""
Script dọn rác và tối ưu các node trên Firebase Realtime Database:
1. Xóa các key rác và các tracking cũ của đơn đã kết thúc trong node 'tracking'
2. Đồng bộ cấu hình sang 'system_config/delivery_countdown' và xóa node dư thừa 'delivery_time_frames'
"""

import sys
import json
import urllib.request

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
    print("=" * 60)
    print("   DỌN RÁC & TỐI ƯU HÓA NODE FIREBASE REALTIME DATABASE")
    print("=" * 60)

    # Đăng nhập Admin
    print("\n[1/3] Đang xác thực quyền Admin...")
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
    auth_res = make_request(auth_url, method="POST", data={
        "email": ADMIN_EMAIL, "password": ADMIN_PASSWORD, "returnSecureToken": True
    })
    id_token = auth_res.get("idToken")
    print(" -> Đăng nhập thành công!")

    # 1. Dọn dẹp node tracking
    print("\n[2/3] Đang dọn dẹp node 'tracking'...")
    orders_url = f"{DATABASE_URL}/orders.json?auth={id_token}"
    orders = make_request(orders_url, method="GET") or {}

    tracking_url = f"{DATABASE_URL}/tracking.json?auth={id_token}"
    trackings = make_request(tracking_url, method="GET") or {}

    removed_tracking = 0
    active_statuses = {"DELIVERING", "ARRIVED_AT_CUSTOMER", "RETURNING_TO_STORE"}

    for t_id in list(trackings.keys()):
        order = orders.get(t_id)
        # Xóa nếu không phải mã đơn hợp lệ hoặc đơn không còn active
        is_invalid_key = not t_id.startswith("OISHI_")
        is_ended = order is not None and order.get("status", "").upper() not in active_statuses
        is_not_found = order is None

        if is_invalid_key or is_ended or is_not_found:
            del_url = f"{DATABASE_URL}/tracking/{t_id}.json?auth={id_token}"
            make_request(del_url, method="DELETE")
            removed_tracking += 1
            print(f" -> Đã xóa tracking cũ: {t_id}")

    print(f" -> Đã xóa {removed_tracking} bản ghi tracking rác / hết hạn.")

    # 2. Xóa node dư thừa delivery_time_frames sau khi đảm bảo system_config đã có
    print("\n[3/3] Đang tối ưu node 'system_config' và xóa 'delivery_time_frames'...")
    dtf_url = f"{DATABASE_URL}/delivery_time_frames.json?auth={id_token}"
    dtf = make_request(dtf_url, method="GET")

    if dtf and isinstance(dtf, dict) and "countdown_config" in dtf:
        cfg = dtf["countdown_config"]
        # Đảm bảo lưu sang system_config
        sys_cfg_url = f"{DATABASE_URL}/system_config/delivery_countdown.json?auth={id_token}"
        make_request(sys_cfg_url, method="PUT", data=cfg)
        print(" -> Đã bảo đảm cấu hình đếm ngược tại 'system_config/delivery_countdown'.")

    # Xóa node delivery_time_frames
    make_request(dtf_url, method="DELETE")
    print(" -> Đã xóa node dư thừa 'delivery_time_frames'.")

    print("\n" + "=" * 60)
    print("   HOÀN TẤT DỌN DẸP DỮ LIỆU TRÊN FIREBASE!")
    print("=" * 60)

if __name__ == "__main__":
    main()
