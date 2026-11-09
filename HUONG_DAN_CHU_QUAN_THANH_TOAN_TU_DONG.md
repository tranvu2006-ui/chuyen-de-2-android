# Hướng Dẫn Tích Hợp Thanh Toán Chuyển Khoản & Hoàn Tiền Tự Động Qua SePay
### (Chuẩn Tự Động Hóa 100% - Không Cần Gửi Mã Cho Admin)

Tài liệu này hướng dẫn chi tiết quy trình thiết lập thanh toán chuyển khoản quét mã VietQR tự động xác nhận và cơ chế tự động hoàn tiền khi hủy đơn cho từng cửa hàng trong hệ sinh thái **Oishi Food**.

Hệ thống đã được cấu hình **Mã khóa bảo mật dùng chung toàn sàn**, do đó chủ quán khi mở cửa hàng chỉ cần cấu hình đúng thông tin trên SePay là **hệ thống tự động kích hoạt ngay lập tức**, không cần gửi mã hay nhờ Admin hỗ trợ.

---

## 1. Tổng quan luồng hoạt động

```
[Khách quét mã VietQR trên App] ──► Chuyển tiền vào STK Ngân hàng của Quán (Nội dung: OIxxxxxxxx)
       ▼
[SePay bắt biến động số dư] ──────► Gửi Webhook tới Cloudflare Worker Oishi (Kèm mã oishi_master_secret_2026)
       ▼
[Cloudflare Worker] ──────────────► Khớp mã chung, kiểm tra đúng STK quán & đúng số tiền đơn
       ▼
[Firebase Database] ──────────────► Tự động cập nhật: paymentStatus = "PAID", isPaid = true
       ▼
[App Khách & App Quán] ───────────► Màn hình nhảy tức thì: ĐÃ THANH TOÁN THÀNH CÔNG (sau 2 - 5 giây)
```

---

## 2. Các Bước Cài Đặt Dành Cho Chủ Quán (Chỉ 3 Bước)

### Bước 1: Mở Quán trên App & Lấy `storeId`
1. Cài đặt app dành cho quán: **`app-internal`**.
2. Bấm **Đăng ký** tài khoản (chọn vai trò Quán), nhập mã OTP gửi về Email để kích hoạt.
3. Đăng nhập vào app -> Vào mục **Hồ sơ quán**:
   - Nhập chính xác: **Tên ngân hàng**, **Số tài khoản (STK)** và **Tên chủ tài khoản** nhận tiền thật của bạn (viết hoa không dấu, ví dụ: `NGUYEN VAN A`).
4. Xem mã cửa hàng **`storeId`** của bạn:
   - Hệ thống tự động đặt mã theo chuẩn: `store_<uid_của_bạn>` (hiển thị trong mục thông tin quán hoặc xem trong Firebase tại `users/{uid}/storeId`).
   - *Ví dụ mẫu*: `store_abc123xyz`

---

### Bước 2: Liên kết tài khoản Ngân hàng trên SePay
1. Đăng nhập vào trang quản trị SePay: [https://my.sepay.vn/](https://my.sepay.vn/).
2. Vào menu **Ngân hàng** ➔ Bấm **Kết nối tài khoản**.
3. Chọn tài khoản ngân hàng của bạn ví dụ: MBbank
4. Chọn loại tài khoản **cá nhân**
5. Ấn **tiếp theo**
6. Chọn **đã có tài khoản** rồi ấn **kết nối ngay**
7. Nhập thông tin tài khoản gồm số tài khoản, tên chủ tài khoản, số cccd, số điện thoại rồi ấn **tiếp tục**
8. Ấn **có, tôi đồng ý**
9. Nhập mã OTP rồi ấn **liên kết API**
10. Bật đồng bộ giao dịch, bật đồng bộ giao dịch tiền vào, đồng bộ giao dịch tiền ra rồi ấn **tiếp theo**

* **Lưu ý**: Đảm bảo số tài khoản ngân hàng kích hoạt trên SePay phải trùng khớp 100% với số tài khoản bạn đã nhập trên App Quán ở Bước 1.

---

### Bước 3: Tạo Webhook trên SePay (Kích hoạt tự động)
1. Trên SePay, vào mục **Tích hợp Webhooks** ➔ Bấm nút **Tạo webhook đầu tiên**.
2. Điền thông tin cấu hình như sau:
   - **Tên webhook**: Đặt tên quán của bạn (ví dụ: `Oishi - Quán Của Tôi`).
   - **URL nhận Webhook**: Điền link Worker của hệ thống kèm `storeId` của bạn:
     ```text
     https://oishi-sepay-webhook.hothivananh180206.workers.dev/webhook?storeId=<storeId_của_quán>
     ```
     *Ví dụ*: Nếu `storeId` của bạn là `store_abc123xyz`, URL sẽ là:
     ```text
     https://oishi-sepay-webhook.hothivananh180206.workers.dev/webhook?storeId=store_abc123xyz
     ```
   - **Loại giao dịch**: Chọn **Tất cả** (**Có tiền vào** và **Có tiền ra**).  
     *(Tiền vào để tự động nhận tiền, Tiền ra để tự động hoàn tiền khi hủy đơn).*
   - **Định dạng dữ liệu**: chọn **JSON** và bật **tự động gửi lại server trả lỗi** rồi ấn tiếp **tiếp theo**
   - **Tài khoản ngân hàng**: chọn **tất cả tài khoản** rồi ấn **tiếp theo**
   - **Kiểu bảo mật**: Chọn **HMAC-SHA256** và nhập **oishi_master_secret_2026**
3. Bấm **Thêm**

🎉 **HOÀN TẤT! Hệ thống tự động hoạt động ngay lập tức**:

---

## 3. Quy Trình Hoàn Tiền Thật Khi Quán Hủy Đơn

1. **Quán hủy đơn**: 
   - Khi hết món hoặc bận, chủ quán bấm **Hủy đơn** trên App Quán (`app-internal`) và nhập lý do hủy.
   - Nếu đơn hàng đã được thanh toán trước đó (`PAID` hoặc đã cọc 20%), hệ thống sẽ tự động chuyển trạng thái đơn sang **Đã hủy (`CANCELLED`)** và tính toán chính xác số tiền cần hoàn trả lại cho khách (`refundAmount`).

2. **Khách cung cấp tài khoản nhận tiền hoàn**: 
   - Trên App Khách (`app-customer`), tại chi tiết đơn hàng đã hủy, hệ thống sẽ hiện thông báo yêu cầu khách nhập thông tin ngân hàng nhận hoàn:
     - Tên ngân hàng thụ hưởng (chọn từ danh sách ngân hàng Việt Nam).
     - Số tài khoản (STK).
     - Tên chủ tài khoản.
   - Sau khi khách lưu thông tin, đơn hàng chuyển sang trạng thái chờ quán chuyển tiền: **`PENDING_REFUND`**.

3. **Quán quét mã VietQR hoàn tiền tự động trên App Quán**:
   - Chủ quán mở App Quán (`app-internal`), vào đơn hàng đã hủy và bấm nút **"Hoàn tiền"**.
   - Ứng dụng tự động lấy thông tin STK của khách và tạo ngay **Mã QR VietQR hoàn tiền**:
     - Đã điền sẵn số tài khoản, tên ngân hàng và số tiền cần hoàn.
     - Đã tự động tạo cú pháp chuyển khoản chứa mã đơn hàng (ví dụ: `TenQuan HT OIxxxxxxxx`).
   - Chủ quán chỉ cần dùng ứng dụng ngân hàng của mình (tài khoản đã kết nối SePay) để **quét mã QR** và bấm **Xác nhận chuyển tiền**.

4. **SePay tự động bắt biến động tiền ra & đổi trạng thái đơn**:
   - Ngay khi tiền chuyển đi từ tài khoản của quán, SePay phát hiện giao dịch **Tiền ra (`transferType: "out"`)**.
   - SePay gửi Webhook về hệ thống để đối soát mã đơn hàng và số tiền hoàn.
   - Hệ thống tự động cập nhật đơn hàng thành **Đã hoàn tiền (`REFUNDED`)**, lưu mã giao dịch hoàn tiền và đồng bộ thông báo hoàn tiền thành công lên màn hình của cả Khách và Quán.

---

## 4. Bảng Xử Lý Sự Cố Thường Gặp (Troubleshooting)

| Hiện tượng | Nguyên nhân | Cách khắc phục |
| :--- | :--- | :--- |
| **SePay báo lỗi 401 Unauthorized** | Nhập sai mã bí mật chung trên SePay. | Kiểm tra lại ô Secret Key/API Key trên SePay, điền chính xác: `oishi_master_secret_2026`. |
| **SePay báo lỗi 400 Bad Request** | URL Webhook bị sai hoặc thiếu `?storeId=...`. | Kiểm tra lại URL Webhook, đảm bảo có đuôi `?storeId=store_...` của quán. |
| **Chuyển tiền thành công nhưng đơn không nhảy PAID** | Số tài khoản nhận tiền trên SePay không khớp với STK nhập trong Hồ sơ quán trên app. | Vào mục Hồ sơ quán trên app kiểm tra và sửa lại STK cho trùng khớp 100% với tài khoản ngân hàng trên SePay. |
| **Hoàn tiền không tự động đổi sang REFUNDED** | Quán khi chuyển tiền hoàn quên ghi mã đơn hàng `OI...` vào nội dung chuyển khoản. | Luôn ghi mã đơn hàng vào nội dung chuyển khoản để SePay nhận diện đúng đơn cần hoàn. |
