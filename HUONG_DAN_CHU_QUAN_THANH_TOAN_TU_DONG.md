# Hướng Dẫn Tích Hợp Thanh Toán Chuyển Khoản Tự Động Qua SePay (Dành Cho Chủ Quán & Quản Trị Viên Oishi)

Tài liệu này hướng dẫn chi tiết quy trình thiết lập thanh toán chuyển khoản quét mã VietQR tự động xác nhận cho từng cửa hàng trong hệ sinh thái **Oishi Food**. Hệ thống sử dụng nền tảng **SePay Webhook** kết nối trực tiếp với **Cloudflare Worker** và **Firebase Realtime Database** của Oishi.

---

## 1. Tổng quan kiến trúc & Luồng hoạt động

```
[Khách hàng quét mã QR] 
       │ (Nội dung CK chứa mã chuẩn: OIxxxxxxxxxxxx)
       ▼
[Ngân hàng của Quán] 
       │ (Biến động số dư tiền vào)
       ▼
[Hệ thống SePay] 
       │ (Tạo Webhook POST kèm chữ ký số HMAC-SHA256)
       ▼
[Cloudflare Worker Oishi] (URL: .../webhook?storeId=<storeId>)
       │
       ├─► 1. Đọc storeId từ URL và tra cứu HMAC Secret tương ứng trong SEPAY_WEBHOOK_SECRETS_JSON
       ├─► 2. Kiểm tra tính hợp lệ của chữ ký số HMAC (x-sepay-signature)
       ├─► 3. Xác minh số tài khoản nhận tiền trùng khớp với hồ sơ quán trên Firebase
       ├─► 4. Kiểm tra mã tham chiếu đơn hàng (paymentReference), số tiền và trạng thái đơn
       │
       ▼ (Nếu tất cả điều kiện thỏa mãn)
[Firebase Realtime Database] ──► Tự động cập nhật paymentStatus: "PAID", status: "PENDING"
       │
       ▼
[App Khách & App Quán] ────────► Màn hình cập nhật tức thì: Đơn hàng đã thanh toán thành công
```

> [!IMPORTANT]
> **Yêu cầu cốt lõi**: Mỗi quán có một mã định danh duy nhất (`storeId`) và một mã khóa bí mật (`HMAC Secret`) riêng biệt do SePay cung cấp. Máy chủ Oishi bắt buộc phải cấu hình chính xác cặp `storeId` ↔ `HMAC Secret` này thì giao dịch mới được xác thực tự động.

---

## 2. Phần I: Dành cho Chủ Quán (Merchant)

### Bước 1: Chuẩn bị thông tin cửa hàng trên Oishi & Lấy `storeId`

1. Đăng nhập vào ứng dụng Oishi dành cho cửa hàng (**App Quán**).
2. Vào phần **Cài đặt cửa hàng** / **Hồ sơ quán**:
   - Cập nhật chính xác **Tên ngân hàng**, **Số tài khoản ngân hàng (STK)** và **Tên chủ tài khoản** nhận tiền kinh doanh của quán.
   - *Lưu ý*: STK này sẽ là tài khoản nhận tiền thực tế khi khách chuyển khoản, bắt buộc phải trùng khớp 100% với tài khoản sẽ tích hợp trên SePay.
3. Lấy mã định danh **`storeId`** của quán:
   - Xem mã cửa hàng hiển thị trong mục thông tin quán trên ứng dụng, hoặc liên hệ Quản trị viên/Hỗ trợ Oishi để lấy mã `storeId` chính thức.
   - *Ví dụ mẫu*: `store_com_tam_sai_gon_01` hoặc chuỗi ID ngẫu nhiên dạng `st_abc123xyz`.
   - *Lưu ý*: `storeId` là mã nhận diện quán trên hệ thống Oishi, **không phải** số tài khoản ngân hàng.

---

### Bước 2: Đăng ký SePay và liên kết tài khoản ngân hàng

1. Truy cập trang quản trị SePay: [https://my.sepay.vn/](https://my.sepay.vn/) và đăng ký tài khoản (hoặc đăng nhập nếu đã có).
2. Tại menu bên trái, chọn **Ngân hàng** ➔ **Thêm tài khoản ngân hàng**.
3. Chọn ngân hàng và tiến hành kết nối tài khoản theo hướng dẫn của SePay (cài ứng dụng SePay trên điện thoại hoặc kết nối cổng Open Banking tùy loại ngân hàng).
4. **Đối chiếu lại**: Đảm bảo số tài khoản ngân hàng vừa kích hoạt thành công trên SePay trùng khớp hoàn toàn với số tài khoản đã đăng ký trên ứng dụng Oishi ở Bước 1.

---

### Bước 3: Tạo Webhook trên SePay gắn đúng `storeId` và lấy HMAC Secret

1. Trên SePay, vào mục **Tích hợp** ➔ **Webhooks** ➔ Bấm nút **Thêm webhook**.
2. Điền thông tin cấu hình webhook chi tiết như sau:
   - **Tên webhook**: Đặt tên dễ nhận diện, ví dụ: `Oishi - Quán Cơm ABC`.
   - **Sự kiện (Event)**: Chọn duy nhất **Có tiền vào** (`transferType = in`). Không cần chọn tiền ra.
   - **Tài khoản áp dụng**: Chọn đúng tài khoản ngân hàng của quán vừa thêm ở Bước 2.
   - **URL nhận Webhook**: Nhập URL máy chủ Oishi kèm tham số `storeId` của quán:
     ```text
     https://oishi-sepay-webhook.hothivananh180206.workers.dev/webhook?storeId=<storeId_cua_quan>
     ```
     *Ví dụ thực tế*: Nếu `storeId` của bạn là `store_quan_123`, URL chính xác phải là:
     ```text
     https://oishi-sepay-webhook.hothivananh180206.workers.dev/webhook?storeId=store_quan_123
     ```
   - **Kiểu bảo mật chữ ký**: Chọn phương thức **HMAC-SHA256**.
   - **HMAC Secret**: Hệ thống SePay sẽ hiển thị (hoặc cho phép sao chép) chuỗi khóa bí mật HMAC (dạng chuỗi ký tự ngẫu nhiên). Bấm sao chép chuỗi này và lưu giữ cẩn thận.
3. Nhấn **Lưu** hoặc **Kích hoạt** để lưu Webhook trên SePay.

---

### Bước 4: Gửi thông tin cho Quản trị viên Oishi kích hoạt

Hệ thống SePay đã tạo webhook nhưng **máy chủ Oishi chưa nhận diện được chữ ký của quán**. Chủ quán cần gửi thông tin qua kênh hỗ trợ kỹ thuật bảo mật của Oishi:

- **Tên cửa hàng**: (Ví dụ: Cơm Tấm Sài Gòn)
- **Mã cửa hàng (`storeId`)**: (Ví dụ: `store_quan_123`)
- **Số tài khoản ngân hàng**: (Ví dụ: `0987654321`)
- **Chuỗi HMAC Secret**: (Chuỗi bí mật sao chép từ SePay ở Bước 3)

> [!WARNING]
> **Quy tắc an toàn bảo mật**:
> - Chỉ gửi `HMAC Secret` qua tin nhắn riêng/email nội bộ cho Quản trị viên Oishi. Không gửi vào các nhóm chat công khai.
> - **Tuyệt đối KHÔNG** gửi mật khẩu SePay, mã OTP ngân hàng, mật khẩu Internet Banking hoặc API Token cá nhân. Oishi **không bao giờ** yêu cầu các thông tin này.

---

## 3. Phần II: Dành cho Quản trị viên / Kỹ thuật Oishi (Cấu hình Máy chủ)

Phần này do người quản trị hệ thống Oishi (có quyền truy cập Cloudflare Worker / Wrangler) thực hiện để nạp `HMAC Secret` của từng quán vào biến môi trường bí mật.

### Cơ chế quản lý Secret trên Cloudflare Worker

Máy chủ Worker sử dụng biến môi trường bí mật **`SEPAY_WEBHOOK_SECRETS_JSON`**. Biến này lưu một chuỗi JSON ánh xạ trực tiếp từng `storeId` với `HMAC Secret` của quán đó:

```json
{
  "storeId_1": "hmac_secret_cua_quan_1",
  "storeId_2": "hmac_secret_cua_quan_2",
  "store_quan_123": "hmac_secret_cua_quan_vua_tao"
}
```

Khi SePay bắn request webhook đến URL `.../webhook?storeId=store_quan_123`, Worker sẽ lấy đúng secret của `store_quan_123` để giải mã và kiểm tra chữ ký trong header `x-sepay-signature`. Nếu khớp, giao dịch được xác thực.

### Các bước thực hiện nạp cấu hình

1. Mở PowerShell hoặc Terminal, di chuyển vào thư mục Worker:
   ```powershell
   cd functions\sepay-worker
   ```

2. **Chuẩn bị chuỗi JSON toàn bộ quán**:
   > [!CAUTION]
   > Lệnh `wrangler secret put` sẽ **ghi đè hoàn toàn** giá trị cũ của biến. Quản trị viên phải mở bản lưu trữ danh sách các quán hiện có, thêm cặp `"storeId_mới": "HMAC_Secret_mới"` vào chuỗi JSON, sau đó nạp lại toàn bộ. Nếu chỉ nạp một quán mới, tất cả các quán cũ sẽ bị mất xác thực và webhook sẽ báo lỗi 401.

   *Mẫu chuỗi JSON (viết trên 1 dòng duy nhất khi dán vào terminal)*:
   ```json
   {"store_quan_1":"secret_1","store_quan_2":"secret_2","store_quan_123":"secret_vua_nhan"}
   ```

3. **Chạy lệnh nạp Secret vào Worker production**:
   ```powershell
   npx wrangler secret put SEPAY_WEBHOOK_SECRETS_JSON --name oishi-sepay-webhook
   ```

4. Khi giao diện dòng lệnh hiện thông báo:
   ```text
   ✔ Enter a secret value:
   ```
   Dán toàn bộ dòng JSON đã chuẩn bị ở trên và nhấn **Enter**.

5. Kiểm tra kết quả hiển thị:
   ```text
   ✨ Success! Uploaded secret SEPAY_WEBHOOK_SECRETS_JSON
   ```

6. Kiểm tra trạng thái Worker đang chạy ổn định:
   ```powershell
   npx wrangler deployments status
   ```

7. Thông báo lại cho Chủ quán rằng hệ thống đã hoàn tất kích hoạt xác thực tự động.

---

## 4. Phần III: Kiểm thử luồng thanh toán thực tế (End-to-End)

Sau khi Quản trị viên xác nhận đã cập nhật Secret trên máy chủ:

1. **Khách hàng đặt đơn**:
   - Mở ứng dụng Oishi Khách hàng, chọn món tại quán vừa cấu hình và tiến hành đặt đơn.
   - Chọn phương thức thanh toán: **Chuyển khoản ngân hàng (VietQR)**.
2. **Quét mã thanh toán**:
   - Ứng dụng Oishi hiển thị mã QR kèm nội dung chuyển khoản tự động có mã nhận diện đơn hàng duy nhất dạng: `OIxxxxxxxxxxxx` (ví dụ: `OI9F8E7D6C5B4A`).
   - Mở app ngân hàng quét mã QR. Kiểm tra kỹ số tiền và giữ nguyên nội dung chuyển khoản, không sửa đổi ký tự nào.
3. **Xác nhận tự động**:
   - Tiền về tài khoản ngân hàng của chủ quán.
   - Trong vòng 3 - 10 giây, SePay bắn webhook đến Worker Oishi.
   - Worker xác thực chữ ký HMAC thành công, kiểm tra đơn hàng và cập nhật đơn sang trạng thái **Đã thanh toán (PAID)**.
   - Ứng dụng của khách hàng và màn hình quản lý đơn hàng của quán (Kanban) tự động chuyển sang trạng thái chờ chế biến món ăn mà không cần chủ quán bấm xác nhận thủ công.

---

## 5. Bảng xử lý sự cố thường gặp (Troubleshooting)

| Hiện tượng | Nguyên nhân có thể | Cách khắc phục |
| :--- | :--- | :--- |
| **SePay báo lỗi 401 Unauthorized** (`Invalid webhook signature`) | 1. URL Webhook trên SePay nhập sai `storeId`.<br>2. Chưa thêm cặp `storeId` ↔ `HMAC Secret` vào máy chủ Oishi.<br>3. Copy thiếu ký tự của chuỗi HMAC Secret khi nạp vào Worker. | - Kiểm tra tham số `?storeId=...` trên URL Webhook của SePay.<br>- Quản trị viên kiểm tra và nạp lại đúng chuỗi JSON bí mật trên Cloudflare Worker. |
| **SePay báo lỗi 400 Bad Request** (`Invalid store`) | `storeId` trên URL chứa ký tự đặc biệt không hợp lệ (ngoài chữ cái, số, gạch dưới, gạch ngang). | Đặt lại `storeId` theo định dạng chuẩn: chỉ gồm chữ cái không dấu, chữ số, dấu `_` hoặc `-`. |
| **SePay báo 200 OK nhưng đơn hàng không đổi trạng thái** (`Receiving account did not match store`) | Số tài khoản nhận tiền trong giao dịch SePay không khớp với số tài khoản lưu trong hồ sơ quán trên Firebase. | Kiểm tra lại mục thông tin ngân hàng trong App Quán trên Oishi và đảm bảo STK trùng khớp 100% với tài khoản SePay. |
| **SePay báo 200 OK nhưng đơn không đổi trạng thái** (`No Oishi payment reference`) | Khách hàng chuyển khoản thủ công và xóa hoặc gõ sai cú pháp nội dung chuyển khoản (thiếu tiền tố `OI...`). | Nhắc khách hàng quét mã QR tự động để giữ nguyên mã tham chiếu đơn hàng. Với trường hợp chuyển sai nội dung, chủ quán kiểm tra sao kê và xác nhận thủ công. |
| **SePay báo 200 OK nhưng đơn không đổi trạng thái** (`Payment amount does not match`) | Khách hàng chuyển số tiền khác với tổng tiền của đơn hàng. | Yêu cầu khách hàng thanh toán đúng chính xác từng chữ số của đơn hàng. |
| **Các quán cũ đột ngột không nhận được thanh toán tự động** | Quản trị viên khi chạy `wrangler secret put` chỉ nhập mỗi quán mới mà quên giữ lại danh sách các quán cũ. | Quản trị viên nạp lại JSON đầy đủ chứa tất cả các quán đã đăng ký từ trước tới nay. |

---

## 6. Tài liệu tham khảo

- Hướng dẫn tích hợp Webhook chính thức từ SePay: [https://docs.sepay.vn/tich-hop-webhooks.html](https://docs.sepay.vn/tich-hop-webhooks.html)
- Xác thực chữ ký số HMAC-SHA256 SePay: [https://docs.sepay.vn/xac-thuc-chu-ky-bao-mat-webhook-hmac.html](https://docs.sepay.vn/xac-thuc-chu-ky-bao-mat-webhook-hmac.html)
- Mã nguồn xử lý Webhook phía máy chủ Oishi: Thư mục `functions/sepay-worker/src/index.js` trong kho mã nguồn dự án.
