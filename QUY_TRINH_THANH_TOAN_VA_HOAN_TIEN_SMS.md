# QUY TRÌNH THANH TOÁN VÀ HOÀN TIỀN TỰ ĐỘNG BẰNG BROADCAST RECEIVER (SMS BANKING)

Tài liệu chuẩn kiến trúc môn học **Lập trình Di Động 2**: Sử dụng thành phần nền tảng Android `BroadcastReceiver` (`Telephony.Sms.Intents.SMS_RECEIVED`) để tự động xử lý trạng thái đơn hàng khi phát hiện biến động số dư ngân hàng qua SMS. Hoàn toàn loại bỏ mọi bên thứ ba (không SePay, không Webhook).

---

## 1. ĐIỀU KIỆN TIÊN QUYẾT (PREREQUISITES)

1. **Đăng ký SMS Banking với Ngân hàng:**
   - **Bên Quán ăn (Nhận tiền thanh toán):** Số tài khoản ngân hàng của Quán phải **đăng ký dịch vụ biến động số dư qua tin nhắn SMS viễn thông** (SMS Banking) gửi về số điện thoại cắm SIM trên máy Quán.
   - **Bên Khách hàng (Nhận tiền hoàn):** Số tài khoản của Khách phải **đăng ký SMS Banking** về số điện thoại cắm SIM trên máy Khách.
   - *Lưu ý ngân hàng thực tế tại Việt Nam:* Đa số các ngân hàng (Vietcombank, MB, VPBank, Techcombank...) chỉ gửi SMS biến động số dư cho giao dịch từ **50.000đ - 100.000đ trở lên** (giao dịch nhỏ chỉ báo qua App OTT Notification).

2. **Cấp quyền trên ứng dụng Android:**
   - Ứng dụng khai báo quyền trong `AndroidManifest.xml`:
     - `android.permission.RECEIVE_SMS`
     - `android.permission.READ_SMS`
   - Người dùng cần cấp quyền "Tin nhắn (SMS)" cho ứng dụng khi cài đặt hoặc trong Cài đặt ứng dụng của máy.

---

## 2. LUỒNG CHUYỂN KHOẢN (THANH TOÁN ĐƠN HÀNG)

```
[Khách hàng]                              [Hệ thống Ngân hàng]                [Máy Quán ăn / App Customer]
     |                                             |                                       |
     |--- 1. Quét VietQR & Chuyển tiền ----------->|                                       |
     |    (Nội dung: DHxxx hoặc ORD_OISHI_xxx)     |                                       |
     |                                             |--- 2. Gửi SMS (+ tiền) -------------->| (SIM máy Quán nhận SMS)
     |                                             |    "TK ...+50,000VND... ND: DHxxx"    |
     |                                             |                                       |--- 3. BankSmsReceiver bắt SMS
     |                                             |                                       |    Trích xuất mã DHxxx
     |                                             |                                       |    Firebase: paymentStatus = "PAID"
     |<====================== 4. Firebase Realtime cập nhật tức thì =======================|
     |   Màn hình PaymentActivity tự động đóng,                                            |
     |   chuyển sang màn hình Đang Chuẩn Bị Món.                                           |
```

* **Cơ chế dự phòng:**
  - Nếu ngân hàng không gửi SMS (do đơn < 100k hoặc mạng viễn thông trễ):
    - Khách hàng quay lại từ app ngân hàng -> `onResume()` tự động kích hoạt hoàn tất.
    - Khách có thể chủ động bấm nút: **`[ ✓ Tôi Đã Thanh Toán Thành Công ➔ ]`**.

---

## 3. LUỒNG HOÀN TIỀN (HỦY ĐƠN HÀNG)

```
[Quán ăn (App Internal)]                  [Hệ thống Ngân hàng]                [Khách hàng (App Customer)]
     |                                             |                                       |
     |--- 1. Bấm [✕ HỦY ĐƠN] trên đơn hàng -------->|                                       |
     |    Hiện mã VietQR hoàn tiền của Khách       |                                       |
     |    Quét QR chuyển tiền hoàn trả Khách       |                                       |
     |    (Nội dung: Hoan tien DHxxx)              |                                       |
     |                                             |--- 2. Gửi SMS (+ tiền hoàn) --------->| (SIM máy Khách nhận SMS)
     |                                             |    "TK ...+50,000VND... Hoan tien..." |
     |                                             |                                       |--- 3. BankSmsReceiver bắt SMS
     |                                             |                                       |    Trích xuất mã DHxxx
     |                                             |                                       |    Firebase: refundStatus = "REFUNDED"
     |                                             |                                       |              status = "CANCELLED"
     |<====================== 4. Firebase Realtime cập nhật tức thì =======================|
     |   Cả 2 bên Quán và Khách đều hiển thị                                               |
     |   trạng thái ĐÃ HOÀN TIỀN (REFUNDED).                                               |
```

* **Cơ chế xác nhận trực tiếp:**
  - Ngay trong hộp thoại hoàn tiền, Quán có thể bấm nút: **`[ ✓ Tôi đã hoàn tiền thành công ➔ ]`** để lập tức đánh dấu đơn đã hoàn trả mà không cần đợi SMS.

---

## 4. CHI TIẾT KỸ THUẬT CỦA `BankSmsReceiver`

- **Tệp nguồn:** `core/.../receiver/BankSmsReceiver.kt`
- **Bộ lọc Intent:** `android.provider.Telephony.SMS_RECEIVED`
- **Biểu thức chính quy (Regex) bắt mã đơn:**
  - `(DH\d+|ORD_OISHI_[A-Za-z0-9_-]+)`: Bắt mã đơn hàng trong nội dung chuyển tiền.
  - Từ khóa hoàn tiền: `hoan tien`, `hoan tra`, `refund`.
- **Cập nhật dữ liệu:**
  - Khi bắt được tin nhắn thanh toán: ghi Firebase `/orders/{orderId}/paymentStatus = "PAID"`.
  - Khi bắt được tin nhắn hoàn tiền: ghi Firebase `/orders/{orderId}/refundStatus = "REFUNDED"` và `status = "CANCELLED"`.
