# OISHI FOOD PLATFORM - HỆ SINH THÁI ĐẶT MÓN 4 ACTOR
> **Đồ Án Chuyên Đề 2 • Nền Tảng Đặt & Giao Món Trực Tuyến Đa Nền Tảng TP.HCM**

Tài liệu chi tiết toàn bộ chức năng của 4 Actor đã được tổng hợp tại:  
👉 **[CHUC_NANG_4_ACTORS.md](file:///c:/Users/Fuck/Downloads/chuyende2/CHUC_NANG_4_ACTORS.md)**

Trang trung tâm trình diễn & mô phỏng 4-in-1:  
👉 **[demo.html](file:///c:/Users/Fuck/Downloads/chuyende2/demo.html)**

---

## Danh Sách 4 Actor & File Chính

| Actor | Vai Trò | Thư Mục / File | Số Màn Hình |
| :--- | :--- | :--- | :---: |
| 🛡️ **Admin System** | Quản trị viên hệ thống, duyệt CCCD, điểm uy tín, xử phạt khiếu nại | [admin.html](file:///c:/Users/Fuck/Downloads/chuyende2/admin.html) | 1 Dashboard (6 Tabs) |
| 👤 **Customer** | Khách hàng tìm món, giỏ hàng, cước ship TP.HCM, thanh toán, QR Token | [khách/](file:///c:/Users/Fuck/Downloads/chuyende2/kh%C3%A1ch/) (`b1.html` -> `b12.html`) | 12 Màn hình |
| 🏪 **Merchant** | Cửa hàng nhận đơn, gán shipper, quản lý thực đơn & combo, báo cáo | [Quán/](file:///c:/Users/Fuck/Downloads/chuyende2/Qu%C3%A1n/) (`1.html` -> `8.html`) | 8 Màn hình |
| 🛵 **Driver** | Tài xế vào ca, nhận đơn phân công, GPS Leaflet lấy & giao món, quét QR | [ship/](file:///c:/Users/Fuck/Downloads/chuyende2/ship/) (`1.html` -> `8.html`) | 8 Màn hình |

Xem chi tiết đầy đủ tại file **[CHUC_NANG_4_ACTORS.md](file:///c:/Users/Fuck/Downloads/chuyende2/CHUC_NANG_4_ACTORS.md)**.

## Cấu hình VNPay

VNPay payment URLs and return signatures are handled by Firebase Cloud Functions; the Hash Secret is stored in Firebase Secret Manager, not in the Android app or repository. Người triển khai cần Node.js 22, Firebase CLI, Firebase project có bật billing (Blaze) và quyền deploy:

```powershell
firebase functions:secrets:set VNPAY_HASH_SECRET --project chuyen-de-2-2f0bc
firebase deploy --only functions,database --project chuyen-de-2-2f0bc
```

Nhập Hash Secret sandbox khi CLI hỏi. Cấu hình website trả kết quả `http://localhost:8080` trong tài khoản VNPay sandbox cho đúng mã terminal. Người dùng tải mã nguồn không cần Hash Secret hoặc quyền deploy; chỉ cần mở project bằng Android Studio, đăng nhập tài khoản khách hàng, tạo đơn và chọn VNPay. Thanh toán thử sandbox bằng NCB: số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày hết hạn `07/15`, OTP `123456`. VNPay chỉ hoạt động sau khi Functions và database rules đã được triển khai.

### Chạy ứng dụng Android

Cài Android Studio và JDK 17, mở thư mục gốc trong Android Studio, chờ Gradle Sync hoàn tất, rồi chạy cấu hình `app-customer` trên emulator hoặc thiết bị. Có thể build APK debug trên Windows bằng `.\gradlew.bat :app-customer:assembleDebug`. Để thanh toán VNPay, project Firebase cấu hình trong `google-services.json` phải có Functions và database rules đã triển khai như hướng dẫn trên.
