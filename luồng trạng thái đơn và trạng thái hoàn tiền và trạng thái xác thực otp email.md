# Luồng trạng thái đơn, hoàn tiền và xác thực OTP email

## I. CÁC TRẠNG THÁI ĐƠN HÀNG (`Order.Status`)

### 1. Giai đoạn thanh toán và chờ duyệt

- **`AWAITING_PAYMENT` (Chờ thanh toán):** Khách chọn chuyển khoản VietQR nhưng giao dịch chưa được hệ thống xác nhận. Khi nhận được webhook hợp lệ và số tiền khớp, đơn chuyển sang `PENDING`.
- **`PENDING` (Chờ quán duyệt):** Đơn đã gửi tới quán để tiếp nhận. Đơn COD vào trạng thái này ngay sau khi khách đặt; đơn trả trước vào đây sau khi hệ thống xác nhận thanh toán.

### 2. Giai đoạn quán chế biến

- **`PREPARING` (Đang chuẩn bị món):** Quán tiếp nhận đơn và bắt đầu làm món.
- **`READY` (Món đã xong):** Quán xác nhận làm xong và đóng gói; đơn chờ shipper nhận đến lấy.

### 3. Giai đoạn shipper giao hàng

- **`ASSIGNED` (Đã có tài xế nhận):** Shipper đã bấm nhận đơn và được gán vào đơn.
- **`ARRIVED_AT_SHOP` (Tài xế đã đến quán):** Shipper cập nhật đã đến quán để lấy món.
- **`DELIVERING` (Đang giao hàng):** Quán đã bàn giao món cho shipper và shipper đang giao đến khách.
- **`ARRIVED_AT_CUSTOMER` (Tài xế đã đến nơi):** Shipper cập nhật đã đến địa chỉ khách và chờ giao nhận.

### 4. Giai đoạn hoàn tất

- **`COMPLETED` (Giao hàng thành công):** Shipper xác minh Delivery Token/QR của khách và xác nhận hoàn tất giao nhận.

## II. GIAI ĐOẠN SỰ CỐ, BOM HÀNG VÀ HỦY ĐƠN

- **`CANCELLED` (Đã hủy đơn):** Đơn bị hủy theo luồng hủy hiện có, chẳng hạn quán không thể thực hiện đơn.
- **`BOOM` (Khách không nhận hàng):** Ghi nhận sự cố khách không nhận đơn theo quy trình xử lý của shipper.
- **`RETURNING_TO_STORE` (Đang trả món về quán):** Shipper đang mang món quay lại quán sau sự cố giao hàng.
- **`RETURNED_TO_STORE` (Đã trả món tại quán):** Món đã được đưa lại quán và quy trình trả món được ghi nhận hoàn tất.

## III. CÁC TRẠNG THÁI HOÀN TIỀN (`Order.RefundStatus`)

Khi đơn `CANCELLED` hoặc `BOOM` có khoản tiền khách đã thanh toán cần trả lại, tiến trình hoàn tiền được theo dõi riêng bằng `refundStatus`. Hủy/bom đơn không tự có nghĩa là tiền đã được hoàn; chỉ chuyển sang `REFUNDED` khi hệ thống ghi nhận giao dịch hoàn thành công.

- **`NONE`:** Chưa phát sinh yêu cầu hoàn tiền.
- **`NOT_REQUIRED`:** Không cần hoàn tiền vì khách chưa thanh toán trước, ví dụ đơn COD chưa thu tiền.
- **`PENDING_INFO`:** Chờ khách nhập thông tin ngân hàng nhận tiền hoàn, gồm tên chủ tài khoản và số tài khoản.
- **`PENDING_REFUND`:** Khách đã cung cấp thông tin; quán kiểm tra người nhận, tạo mã QR và thực hiện chuyển khoản hoàn.
- **`REFUNDED`:** SePay/hệ thống đã ghi nhận giao dịch chuyển tiền hoàn thành công.

## IV. CÁC TRẠNG THÁI XÁC THỰC OTP EMAIL

Trạng thái xác thực email được lưu tại `users/{uid}/emailVerified`. OTP được gửi và xác minh qua Supabase; Firebase Auth được dùng để tạo tài khoản và đăng nhập.

- **Chưa xác thực — `emailVerified = false` hoặc chưa có giá trị:** Người dùng chưa xác minh OTP. Khi đăng ký, tài khoản Firebase chưa được tạo cho đến khi OTP hợp lệ. Khi đăng nhập tài khoản chưa xác minh, ứng dụng yêu cầu OTP trước khi hoàn tất đăng nhập.
- **Đang chờ OTP:** Supabase đã nhận yêu cầu gửi mã đến email; người dùng cần nhập mã trong ứng dụng. Trạng thái chờ này không đồng nghĩa email đã xác thực.
- **OTP sai hoặc hết hạn:** Ứng dụng báo lỗi, không cho hoàn tất xác thực và không đặt `emailVerified = true`.
- **Đã xác thực — `emailVerified = true`:** OTP hợp lệ; ứng dụng hoàn tất đăng ký hoặc tiếp tục đăng nhập. Những lần đăng nhập sau không cần xác minh lại OTP.

Template email Supabase cần hiển thị `{{ .Token }}` để người dùng nhận được mã OTP. Tài khoản shipper do quán tạo có `role = DRIVER`/`SHIPPER` và `storeId` không rỗng được miễn OTP theo luồng hiện tại.
