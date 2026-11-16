# -*- coding: utf-8 -*-
"""
Script bật/tắt tính năng Bỏ qua mã OTP khi đăng nhập (Bypass Login OTP)
trực tiếp trên Firebase Realtime Database tại node 'system_config/bypass_login_otp'.

Cách sử dụng:
  - Xem trạng thái hiện tại:
      python scripts/toggle_login_otp.py
  - TẮT OTP (Vào thẳng không cần mã):
      python scripts/toggle_login_otp.py on
  - BẬT LẠI OTP (Bắt buộc nhập mã OTP):
      python scripts/toggle_login_otp.py off
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

def get_admin_token():
    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
    auth_res = make_request(auth_url, method="POST", data={
        "email": ADMIN_EMAIL, "password": ADMIN_PASSWORD, "returnSecureToken": True
    })
    return auth_res.get("idToken")

def main():
    args = sys.argv[1:]
    id_token = get_admin_token()

    target_url = f"{DATABASE_URL}/system_config/bypass_login_otp.json?auth={id_token}"

    if not args or args[0].lower() in ["status", "check"]:
        current_status = make_request(target_url, method="GET")
        print("\n" + "=" * 55)
        print(" TRẠNG THÁI XÁC THỰC OTP ĐĂNG NHẬP (FIREBASE CONFIG)")
        print("=" * 55)
        if current_status is True:
            print(" -> [Bypass OTP: ON] (Đang VÔ HIỆU HÓA OTP: Vào thẳng luôn!)")
        elif current_status is False:
            print(" -> [Bypass OTP: OFF] (Đang BẬT OTP: Bắt buộc xác thực mã OTP)")
        else:
            print(" -> [Bypass OTP: CHƯA CẤU HÌNH] (Mặc định trong code: ON - Vào thẳng)")
        print("=" * 55)
        print(" Cú pháp đổi trạng thái:")
        print("   python scripts/toggle_login_otp.py on   # Tắt OTP (vào thẳng)")
        print("   python scripts/toggle_login_otp.py off  # Bật lại OTP")
        print("=" * 55 + "\n")
        return

    action = args[0].lower()
    if action in ["on", "true", "1", "bypass", "disable-otp"]:
        make_request(target_url, method="PUT", data=True)
        print("\n -> [THÀNH CÔNG] Đã VÔ HIỆU HÓA mã OTP đăng nhập trên Firebase!")
        print("    Tất cả thiết bị mở app giờ đây sẽ vào thẳng màn hình chính sau khi nhập Email + Mật khẩu.\n")
    elif action in ["off", "false", "0", "enable-otp"]:
        make_request(target_url, method="PUT", data=False)
        print("\n -> [THÀNH CÔNG] Đã KÍCH HOẠT LẠI mã OTP đăng nhập trên Firebase!")
        print("    Tất cả tài khoản sẽ phải nhập mã OTP Supabase gửi qua Email để đăng nhập.\n")
    else:
        print(f"\n [!] Tham số không hợp lệ: '{args[0]}'. Sử dụng 'on' hoặc 'off'.")

if __name__ == "__main__":
    main()
