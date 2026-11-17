# TÀI LIỆU GIẢI THÍCH CHI TIẾT CÁC NODE TRÊN FIREBASE REALTIME DATABASE
## DỰ ÁN: HỆ THỐNG ĐẶT VÀ GIAO ĐỒ ĂN TRỰC TUYẾN ĐA PHÂN HỆ OISHI FOOD
**Cơ sở dữ liệu:** `https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app/`  
**Khu vực lưu trữ (Location):** Singapore (`asia-southeast1`)  
**Công nghệ:** Firebase Realtime Database (NoSQL JSON Tree) kết hợp Firebase Authentication

---

## TỔNG QUAN KIẾN TRÚC CƠ SỞ DỮ LIỆU NO-SQL
Cơ sở dữ liệu Firebase Realtime Database của Oishi Food được tổ chức theo mô hình cây JSON phẳng (Flattened JSON Tree) tối ưu hóa cho truy vấn thời gian thực (Realtime Listeners), giảm độ trễ (latency < 200ms) và phục vụ đồng thời cho 3 ứng dụng:
1. **`app-customer`**: Ứng dụng dành cho Khách hàng (Đặt món, giỏ hàng, thanh toán, tracking, chat, hủy đơn).
2. **`app-internal`**: Ứng dụng dành cho Quán ăn (Quản lý đơn, KDS Bếp, thực đơn, báo cáo) & Tài xế (Nhận đơn, GPS, quét QR/PIN, trả món).
3. **`app-admin`**: Ứng dụng dành cho Quản trị viên (Duyệt quán eKYC, quản lý danh mục, xử lý khiếu nại, cấu hình toàn sàn).

---

## DANH SÁCH TOÀN BỘ 13 ROOT NODES TRÊN FIREBASE

```
https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app/
├── boss_stores          # Quản lý liên kết Chuỗi nhà hàng & các Chi nhánh
├── carts                # Giỏ hàng thời gian thực của Khách hàng
├── categories           # Danh mục ngành hàng cấp hệ thống toàn sàn
├── chefs                # Đội ngũ Đầu bếp màn hình KDS của các quán ăn
├── customers            # Hồ sơ Khách hàng & Sổ địa chỉ giao hàng
├── drivers              # Hồ sơ Tài xế (Shipper), phương tiện & trạng thái trực
├── merchants            # Hồ sơ Chủ quán ăn / Thu ngân cửa hàng
├── orders               # Bảng trung tâm lưu trữ toàn bộ Đơn hàng của hệ thống
├── stores               # Hồ sơ Quán ăn, Thực đơn món ăn, Combo & Đánh giá
├── system_config        # Cấu hình toàn sàn (Phí ship, Đếm ngược, Cọc, Uy tín)
├── tracking             # Tọa độ GPS thời gian thực của Tài xế đang giao đơn
├── trust_score_logs     # Nhật ký lịch sử biến động Điểm uy tín của Khách hàng
└── users                # Bảng tài khoản người dùng tập trung (Auth & Phân quyền)
```

---

## CHI TIẾT TỪNG NODE TRÊN FIREBASE

### 1. Node `users` — Bảng tài khoản người dùng tập trung
- **Đường dẫn (Path):** `/users/{userId}`
- **Ý nghĩa & Mục đích:** Quản lý thông tin định danh, tài khoản đăng nhập, trạng thái khóa/mở và vai trò (Role) của tất cả người dùng trong toàn bộ hệ thống Oishi Food.
- **Các Actor truy cập:** Tất cả các App (Đăng nhập, Đăng ký, AccountLockWatcher kiểm tra khóa tài khoản tức thời).
- **Cấu trúc trường dữ liệu (Schema):**
  - `uid` *(String)*: Mã định danh duy nhất của tài khoản (khớp với Firebase Auth UID).
  - `name` *(String)*: Họ và tên hiển thị của người dùng.
  - `email` *(String)*: Địa chỉ Gmail dùng để đăng nhập và nhận mã OTP.
  - `phone` *(String)*: Số điện thoại liên lạc chính thức.
  - `role` *(String)*: Quyền hạn của tài khoản: `CUSTOMER` (Khách hàng), `MERCHANT` (Chủ quán), `DRIVER` (Tài xế), `ADMIN` (Quản trị viên), `BOSS` (Chủ chuỗi), `CHEF` (Đầu bếp).
  - `roleId` *(Int)*: Mã số vai trò tương ứng (1: Admin, 2: Customer, 3: Merchant, 4: Driver, 5: Boss, 6: Chef).
  - `status` *(String)*: Trạng thái tài khoản: `ACTIVE` (Đang hoạt động), `LOCKED` (Bị khóa do gian lận/bom hàng), `INACTIVE` (Chờ kích hoạt).
  - `statusId` *(Int)*: Mã số trạng thái (1: Active, 2: Locked/Inactive).
  - `trustScore` *(Int)*: Điểm uy tín của tài khoản (Mặc định khi tạo mới là 100 điểm).
  - `avatarUrl` *(String)*: Đường dẫn ảnh đại diện của người dùng.
  - `createdAt` *(Long)*: Thời gian đăng ký tài khoản (dạng mili-giây Epoch Unix).
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "users": {
    "USR_CUST_001": {
      "uid": "USR_CUST_001",
      "name": "Nguyễn Văn An",
      "email": "khach01@gmail.com",
      "phone": "0901234567",
      "role": "CUSTOMER",
      "roleId": 2,
      "status": "ACTIVE",
      "statusId": 1,
      "trustScore": 100,
      "avatarUrl": "https://storage.../avatars/u1.png",
      "createdAt": 1731800000000
    }
  }
}
```

---

### 2. Node `customers` — Hồ sơ khách hàng & Sổ địa chỉ
- **Đường dẫn (Path):** `/customers/{customerId}`
- **Ý nghĩa & Mục đích:** Lưu trữ hồ sơ mở rộng của khách hàng, đặc biệt là danh sách sổ địa chỉ nhận hàng (nhà riêng, công ty) phục vụ tính toán tự động khoảng cách và định vị trên bản đồ.
- **Các Actor truy cập:** Khách hàng (app-customer), Tài xế (xem địa chỉ giao), Quản trị viên.
- **Cấu trúc trường dữ liệu (Schema):**
  - `customerId` *(String)*: Mã khách hàng (trùng với `userId`).
  - `defaultAddress` *(String)*: Địa chỉ giao hàng mặc định được chọn khi mở ứng dụng.
  - `defaultLatitude` / `defaultLongitude` *(Double)*: Tọa độ GPS của địa chỉ nhận hàng mặc định.
  - `addresses` *(Node con)*: Danh bạ các địa chỉ đã lưu của khách hàng:
    - `{addressId}/addressTitle` *(String)*: Tên gợi nhớ (ví dụ: "Nhà riêng", "Công ty").
    - `{addressId}/fullAddress` *(String)*: Địa chỉ chi tiết (Số nhà, tên đường, Phường, Quận, TP.HCM).
    - `{addressId}/district` *(String)*: Quận/Huyện (dùng tính phí ship nội quận / ngoại quận).
    - `{addressId}/latitude` / `longitude` *(Double)*: Tọa độ GPS chính xác.
    - `{addressId}/isDefault` *(Boolean)*: Cờ đánh dấu địa chỉ mặc định.
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "customers": {
    "USR_CUST_001": {
      "customerId": "USR_CUST_001",
      "defaultAddress": "Số 20 Đường Tăng Nhơn Phú, P. Phước Long B, TP. Thủ Đức",
      "defaultLatitude": 10.8504,
      "defaultLongitude": 106.7720,
      "addresses": {
        "ADDR_01": {
          "addressTitle": "Trường TDC",
          "fullAddress": "53 Võ Văn Ngân, Linh Chiểu, Thủ Đức, TP.HCM",
          "district": "Thành phố Thủ Đức",
          "latitude": 10.8519,
          "longitude": 106.7584,
          "isDefault": true
        }
      }
    }
  }
}
```

---

### 3. Node `stores` — Thông tin Quán ăn & Thực đơn món
- **Đường dẫn (Path):** `/stores/{storeId}`
- **Ý nghĩa & Mục đích:** Node quan trọng bậc nhất lưu trữ toàn bộ hồ sơ nhà hàng/quán ăn, thực đơn món ăn, danh mục, combo, giờ hoạt động và tình trạng kiểm duyệt eKYC.
- **Các Actor truy cập:** Quán ăn (app-internal cập nhật menu), Khách hàng (xem quán & món), Admin (duyệt quán).
- **Cấu trúc trường dữ liệu (Schema):**
  - `id` *(String)*: Mã định danh duy nhất của quán ăn (ví dụ: `STR_01`, `STR_VUD2`).
  - `name` *(String)*: Tên thương mại của quán ăn (ví dụ: "Cơm Tấm Phúc Lộc Thọ", "Quán VŨ 2").
  - `address` *(String)*: Địa chỉ thực tế của quán ăn.
  - `latitude` / `longitude` *(Double)*: Tọa độ GPS của quán (dùng vẽ lộ trình tài xế và tính km giao hàng).
  - `phone` *(String)*: Số điện thoại hotline của quán.
  - `merchantId` *(String)*: Mã tài khoản người sở hữu/quản lý quán.
  - `bossId` *(String)*: Mã Chủ chuỗi nếu quán này là một chi nhánh.
  - `status` *(String)*: Trạng thái phê duyệt: `APPROVED` (Đã duyệt mở bán), `PENDING` (Chờ duyệt eKYC), `REJECTED` (Bị từ chối).
  - `isOpen` *(Boolean)*: Trạng thái đóng/mở cửa của quán (Bật/tắt thủ công hoặc theo lịch).
  - `openingHours` / `closingHours` *(String)*: Khung giờ mở cửa (ví dụ: "07:00", "22:00").
  - `rating` *(Double)*: Điểm đánh giá trung bình của quán (tính theo số sao 1.0 - 5.0).
  - `ratingCount` *(Int)*: Tổng số lượt đánh giá từ khách hàng.
  - `imageUrl` / `bannerUrl` *(String)*: Ảnh đại diện và ảnh bìa của quán ăn.
  - `dishes` *(Node con)*: Danh sách các món ăn trực thuộc quán:
    - `{dishId}/name` *(String)*: Tên món ăn (ví dụ: "Cơm sườn nướng mật ong").
    - `{dishId}/price` *(Long)*: Đơn giá món (VNĐ).
    - `{dishId}/description` *(String)*: Mô tả chi tiết món.
    - `{dishId}/imageUrl` *(String)*: Ảnh chụp món ăn.
    - `{dishId}/categoryId` *(String)*: Khóa ngoại trỏ đến danh mục ngành hàng.
    - `{dishId}/isAvailable` *(Boolean)*: Còn hàng (`true`) hoặc Tạm hết món (`false`).
    - `{dishId}/cookingTimeMinutes` *(Int)*: Thời gian nấu dự kiến (dùng cho KDS bếp).
  - `combos` *(Node con)*: Các gói combo khuyến mãi giảm giá của quán.
  - `storeCategories` *(Node con)*: Danh mục phân loại riêng do quán tự định nghĩa.
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "stores": {
    "STR_01": {
      "id": "STR_01",
      "name": "Quán Cơm Tấm Oishi Thủ Đức",
      "address": "Số 15 Võ Văn Ngân, Linh Chiểu, Thủ Đức",
      "latitude": 10.8512,
      "longitude": 106.7580,
      "phone": "0988776655",
      "merchantId": "USR_MERCHANT_01",
      "status": "APPROVED",
      "isOpen": true,
      "openingHours": "06:30",
      "closingHours": "21:30",
      "rating": 4.9,
      "ratingCount": 128,
      "dishes": {
        "DISH_01": {
          "id": "DISH_01",
          "name": "Cơm Sườn Bì Chả Đặc Biệt",
          "price": 45000,
          "isAvailable": true,
          "cookingTimeMinutes": 10,
          "categoryId": "CAT_COM"
        }
      }
    }
  }
}
```

---

### 4. Node `categories` — Danh mục ngành hàng toàn sàn
- **Đường dẫn (Path):** `/categories/{categoryId}`
- **Ý nghĩa & Mục đích:** Quản lý danh sách các ngành hàng ẩm thực chung trên toàn hệ thống (Cơm, Bún/Phở, Trà sữa, Gà rán, Ăn vặt...) hiển thị dạng lưới trên Trang chủ của Khách hàng.
- **Các Actor truy cập:** Admin (Thêm/Sửa/Xóa danh mục), Khách hàng (Lọc món ăn theo danh mục).
- **Cấu trúc trường dữ liệu (Schema):**
  - `categoryId` *(String)*: Mã danh mục (ví dụ: `CAT_COM`, `CAT_TRASUA`).
  - `categoryName` *(String)*: Tên hiển thị (ví dụ: "Cơm Tấm", "Trà Sữa & Đồ Uống").
  - `iconUrl` *(String)*: Đường dẫn ảnh icon đại diện của danh mục.
  - `displayOrder` *(Int)*: Thứ tự hiển thị ưu tiên trên giao diện Trang chủ.
  - `isActive` *(Boolean)*: Trạng thái hiển thị (`true` là cho phép chọn).
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "categories": {
    "CAT_COM": {
      "categoryId": "CAT_COM",
      "categoryName": "Cơm",
      "iconUrl": "https://storage.../icons/com.png",
      "displayOrder": 1,
      "isActive": true
    },
    "CAT_TRASUA": {
      "categoryId": "CAT_TRASUA",
      "categoryName": "Trà Sữa",
      "iconUrl": "https://storage.../icons/trasua.png",
      "displayOrder": 2,
      "isActive": true
    }
  }
}
```

---

### 5. Node `carts` — Giỏ hàng thời gian thực của Khách hàng
- **Đường dẫn (Path):** `/carts/{userId}/{storeId}`
- **Ý nghĩa & Mục đích:** Lưu trữ giỏ hàng đang chọn của người dùng trên đám mây. Sử dụng thuật toán Debounce chống giật lag và cơ chế đơn quán (mỗi thời điểm chỉ đặt món tại 1 cửa hàng duy nhất).
- **Các Actor truy cập:** Khách hàng (app-customer).
- **Cấu trúc trường dữ liệu (Schema):**
  - `shopId` *(String)*: Mã quán ăn mà khách đang chọn món.
  - `shopName` *(String)*: Tên quán ăn.
  - `shopAddress` *(String)*: Địa chỉ quán ăn.
  - `totalAmount` *(Long)*: Tổng giá trị tiền món tạm tính (chưa tính phí ship).
  - `items` *(Node con)*: Danh sách chi tiết các món ăn trong giỏ:
    - `{itemId}/itemId` *(String)*: Mã món ăn.
    - `{itemId}/name` *(String)*: Tên món ăn.
    - `{itemId}/price` *(Long)*: Đơn giá.
    - `{itemId}/quantity` *(Int)*: Số lượng đặt (ví dụ: 1, 2, 3).
    - `{itemId}/imageUrl` *(String)*: Ảnh minh họa món.
    - `{itemId}/options` *(String)*: Tùy chọn đi kèm (ví dụ: "Ít cay, không hành").
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "carts": {
    "USR_CUST_001": {
      "STR_01": {
        "shopId": "STR_01",
        "shopName": "Quán Cơm Tấm Oishi Thủ Đức",
        "totalAmount": 90000,
        "items": {
          "DISH_01": {
            "itemId": "DISH_01",
            "name": "Cơm Sườn Bì Chả Đặc Biệt",
            "price": 45000,
            "quantity": 2,
            "options": "Thêm ớt, không mỡ hành"
          }
        }
      }
    }
  }
}
```

---

### 6. Node `orders` — Bảng dữ liệu Đơn hàng trung tâm
- **Đường dẫn (Path):** `/orders/{orderId}`
- **Ý nghĩa & Mục đích:** Node quan trọng nhất trong vòng đời vận hành hệ thống. Lưu trữ toàn bộ dữ liệu giao dịch từ khi tạo đơn, thanh toán VietQR, duyệt đơn, Bếp nấu, Tài xế nhận cuốc, giao hàng QR/PIN đến khi hoàn thành hoặc xử lý hủy đơn trả hàng.
- **Các Actor truy cập:** Khách hàng (TrackingActivity), Quán ăn (OrderListFragment), Bếp (ChefFragment), Tài xế (DeliveryTripActivity), Admin (Giám sát).
- **Cấu trúc trường dữ liệu (Schema):**
  - `orderId` *(String)*: Mã đơn hàng (ví dụ: `ORD_1731801234`, hiển thị `#OISHI_VQW1XA`).
  - `customerId` *(String)*: Mã khách hàng đặt đơn.
  - `customerName` / `customerPhone` *(String)*: Tên và số điện thoại người nhận.
  - `deliveryAddress` *(String)*: Địa chỉ nhận hàng chi tiết của khách.
  - `storeId` *(String)*: Mã quán ăn tiếp nhận đơn.
  - `storeName` / `storeAddress` *(String)*: Tên và địa chỉ quán.
  - `shipperId` *(String)*: Mã tài xế nhận cuốc giao hàng (rỗng nếu chưa có tài xế).
  - `shipperName` / `shipperPhone` *(String)*: Tên và số điện thoại tài xế.
  - `status` *(String)*: Trạng thái vòng đời đơn hàng:
    - `AWAITING_PAYMENT`: Chờ thanh toán VietQR (đếm ngược 15 phút).
    - `PENDING`: Đã đặt, chờ Quán ăn bấm xác nhận.
    - `PREPARING`: Quán đang chế biến (Bếp KDS đếm giờ nấu).
    - `READY`: Quán nấu xong, chờ tài xế đến lấy món.
    - `ASSIGNED`: Tài xế đã nhận cuốc xe, đang đến quán.
    - `DELIVERING`: Tài xế đã lấy món, đang chạy giao cho khách.
    - `ARRIVED_AT_CUSTOMER`: Tài xế đã đến trước cửa nhà khách.
    - `COMPLETED`: Giao thành công (quét mã QR hoặc nhập PIN hợp lệ).
    - `CANCELLED`: Đơn bị hủy (kèm kiểm tra điểm uy tín và hoàn tiền).
    - `RETURNING_TO_STORE`: Khách hủy lúc đang giao -> Tài xế mang món về quán.
    - `RETURNED_TO_STORE`: Quán xác nhận đã nhận lại đồ ăn trả về an toàn.
  - `paymentMethod` *(String)*: Phương thức thanh toán: `COD` (Tiền mặt), `VIETQR` (Chuyển khoản online), `DEPOSIT` (Đặt cọc 30%).
  - `paymentStatus` *(String)*: Trạng thái thanh toán: `UNPAID` (Chưa trả), `PAID` (Đã thanh toán đủ), `DEPOSITED` (Đã trả phần cọc).
  - `isPaid` *(Boolean)*: Cờ đánh dấu đã nhận được tiền.
  - `isFullPaid` *(Boolean)*: Cờ đánh dấu đã trả đủ 100% (căn cứ để không trừ điểm uy tín khi hủy đơn).
  - `subtotal` *(Long)*: Tiền món ăn.
  - `shippingFee` *(Long)*: Cước phí giao hàng (tính theo km).
  - `discountAmount` *(Long)*: Tiền giảm giá từ khuyến mãi.
  - `totalAmount` *(Long)*: Tổng số tiền thanh toán cuối cùng của đơn hàng.
  - `depositAmount` *(Long)*: Số tiền cọc đã thanh toán (nếu áp dụng cọc).
  - `verificationPin` *(String)*: Mã PIN bảo mật 6 số giao hàng (ví dụ: "648291").
  - `cancelledBy` *(String)*: Bên thực hiện hủy đơn (`CUSTOMER`, `MERCHANT`, `ADMIN`).
  - `cancelReason` *(String)*: Lý do hủy đơn hàng.
  - `items` *(List/Map)*: Danh sách chi tiết các món ăn đặt trong đơn.
  - `createdAt` / `updatedAt` *(Long)*: Mốc thời gian tạo và cập nhật đơn hàng.
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "orders": {
    "ORD_1731801234": {
      "orderId": "ORD_1731801234",
      "customerId": "USR_CUST_001",
      "customerName": "Nguyễn Văn An",
      "customerPhone": "0901234567",
      "deliveryAddress": "53 Võ Văn Ngân, Linh Chiểu, Thủ Đức",
      "storeId": "STR_01",
      "storeName": "Quán Cơm Tấm Oishi Thủ Đức",
      "shipperId": "USR_DRV_01",
      "status": "DELIVERING",
      "paymentMethod": "VIETQR",
      "paymentStatus": "PAID",
      "isPaid": true,
      "isFullPaid": true,
      "subtotal": 90000,
      "shippingFee": 15000,
      "totalAmount": 105000,
      "verificationPin": "648291",
      "createdAt": 1731801234000
    }
  }
}
```

---

### 7. Node `tracking` — Tọa độ GPS tài xế thời gian thực
- **Đường dẫn (Path):** `/tracking/{orderId}` hoặc `/tracking/{driverId}`
- **Ý nghĩa & Mục đích:** Nhận dữ liệu tọa độ GPS cập nhật liên tục từ `LocationForegroundService` của Tài xế (mỗi 3 - 5 giây). Cho phép Khách hàng nhìn thấy biểu tượng xe máy di chuyển mượt mà trên Google Maps mà không cần bấm Refresh màn hình.
- **Các Actor truy cập:** Tài xế (Ghi tọa độ ngầm), Khách hàng (Lắng nghe để vẽ Marker trên bản đồ).
- **Cấu trúc trường dữ liệu (Schema):**
  - `lat` *(Double)*: Vĩ độ GPS hiện tại của tài xế.
  - `lng` *(Double)*: Kinh độ GPS hiện tại của tài xế.
  - `updatedAt` *(Long)*: Mốc thời gian cập nhật vị trí gần nhất (mili-giây).
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "tracking": {
    "ORD_1731801234": {
      "lat": 10.85215,
      "lng": 106.76120,
      "updatedAt": 1731801550000
    }
  }
}
```

---

### 8. Node `drivers` — Hồ sơ Tài xế & Trạng thái hoạt động
- **Đường dẫn (Path):** `/drivers/{driverId}`
- **Ý nghĩa & Mục đích:** Quản lý thông tin phương tiện giao hàng, biển số xe, ca trực (Online/Offline), trạng thái đang bận giao hay sẵn sàng nhận đơn, và số cuốc xe hoàn thành.
- **Các Actor truy cập:** Tài xế (app-internal bật/tắt nhận cuốc), Quán ăn & Khách (xem thông tin người giao), Admin.
- **Cấu trúc trường dữ liệu (Schema):**
  - `driverId` *(String)*: Mã tài xế (trùng với `userId`).
  - `vehicleType` *(String)*: Loại phương tiện (ví dụ: "Xe máy Honda Wave").
  - `licensePlate` *(String)*: Biển số đăng ký xe (ví dụ: "59X3-123.45").
  - `isOnline` *(Boolean)*: Cờ bật nhận đơn (`true` là đang online sẵn sàng nhận cuốc).
  - `isBusy` *(Boolean)*: Đang bận thực hiện chuyến giao (`true` là đang chở đơn).
  - `activeOrderId` *(String)*: Mã đơn hàng đang thực hiện (rỗng nếu rảnh).
  - `currentLat` / `currentLng` *(Double)*: Vị trí tọa độ mới nhất của tài xế.
  - `rating` *(Double)*: Điểm đánh giá trung bình của tài xế (1.0 - 5.0 sao).
  - `completedTripsCount` *(Int)*: Tổng số cuốc xe đã giao thành công.
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "drivers": {
    "USR_DRV_01": {
      "driverId": "USR_DRV_01",
      "vehicleType": "Honda AirBlade 125cc",
      "licensePlate": "59-X2 888.99",
      "isOnline": true,
      "isBusy": true,
      "activeOrderId": "ORD_1731801234",
      "currentLat": 10.85215,
      "currentLng": 106.76120,
      "rating": 4.95,
      "completedTripsCount": 154
    }
  }
}
```

---

### 9. Node `chefs` — Đội ngũ Đầu bếp KDS của Nhà hàng
- **Đường dẫn (Path):** `/chefs/{chefId}`
- **Ý nghĩa & Mục đích:** Quản lý tài khoản và ca làm việc của đội ngũ đầu bếp tại các chi nhánh quán ăn. Kết nối trực tiếp với màn hình nhà bếp thông minh (ChefFragment) để nhận đơn cần chế biến.
- **Các Actor truy cập:** Đầu bếp (app-internal xem hàng đợi nấu), Quán ăn (phân công đầu bếp), Boss (quản lý nhân sự).
- **Cấu trúc trường dữ liệu (Schema):**
  - `chefId` *(String)*: Mã tài khoản đầu bếp.
  - `storeId` *(String)*: Mã cửa hàng/chi nhánh mà đầu bếp đang làm việc.
  - `name` *(String)*: Tên đầu bếp.
  - `phone` *(String)*: Số điện thoại liên hệ.
  - `title` *(String)*: Chức danh chuyên môn (ví dụ: "Bếp trưởng", "Bếp phụ").
  - `activeOrdersCount` *(Int)*: Số lượng đơn hàng hiện tại đang phụ trách nấu.
  - `isOnline` *(Boolean)*: Đang trong ca trực nấu (`true`) hoặc nghỉ ca (`false`).
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "chefs": {
    "CHEF_01": {
      "chefId": "CHEF_01",
      "storeId": "STR_01",
      "name": "Trần Văn Bếp",
      "title": "Bếp trưởng ca sáng",
      "activeOrdersCount": 3,
      "isOnline": true
    }
  }
}
```

---

### 10. Node `merchants` — Hồ sơ Chủ quán & Thu ngân
- **Đường dẫn (Path):** `/merchants/{merchantId}`
- **Ý nghĩa & Mục đích:** Liên kết tài khoản người dùng có quyền `MERCHANT` với cửa hàng tương ứng mà họ phụ trách quản lý, cấu hình thiết bị đọc thông báo ngân hàng VietQR.
- **Các Actor truy cập:** Quán ăn (app-internal), Quản trị viên, Chủ chuỗi (Boss).
- **Cấu trúc trường dữ liệu (Schema):**
  - `merchantId` *(String)*: Mã tài khoản chủ quán (trùng với `userId`).
  - `storeId` *(String)*: Mã cửa hàng được phân quyền quản lý.
  - `roleInStore` *(String)*: Vai trò trong quán (`STORE_OWNER` - Chủ quán, `CASHIER` - Thu ngân).
  - `bankNotificationEnabled` *(Boolean)*: Cờ cho phép dịch vụ tự động đọc thông báo biến động số dư VietQR (`BankNotificationListenerService`).
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "merchants": {
    "USR_MERCHANT_01": {
      "merchantId": "USR_MERCHANT_01",
      "storeId": "STR_01",
      "roleInStore": "STORE_OWNER",
      "bankNotificationEnabled": true
    }
  }
}
```

---

### 11. Node `boss_stores` — Quản lý Chi nhánh Chuỗi nhà hàng
- **Đường dẫn (Path):** `/boss_stores/{bossId}/{storeId}`
- **Ý nghĩa & Mục đích:** Phục vụ mô hình chuỗi F&B nhiều chi nhánh. Cho phép tài khoản Chủ chuỗi (`BOSS`) quản trị đồng thời nhiều cơ sở kinh doanh, xem báo cáo doanh thu tổng hợp và điều chuyển đơn hàng giữa các chi nhánh.
- **Các Actor truy cập:** Chủ chuỗi (BossDashboardActivity trong app-internal).
- **Cấu trúc trường dữ liệu (Schema):**
  - `storeId` *(String)*: Mã chi nhánh trực thuộc.
  - `storeName` *(String)*: Tên chi nhánh (ví dụ: "Chi nhánh 1 - Thủ Đức", "Chi nhánh 2 - Quận 9").
  - `assignedAt` *(Long)*: Ngày thành lập / thêm chi nhánh vào chuỗi.
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "boss_stores": {
    "USR_BOSS_99": {
      "STR_01": {
        "storeId": "STR_01",
        "storeName": "Cơm Tấm Oishi - Chi nhánh Thủ Đức",
        "assignedAt": 1731700000000
      },
      "STR_02": {
        "storeId": "STR_02",
        "storeName": "Cơm Tấm Oishi - Chi nhánh Quận 1",
        "assignedAt": 1731750000000
      }
    }
  }
}
```

---

### 12. Node `trust_score_logs` — Nhật ký Điểm uy tín chống bùng hàng
- **Đường dẫn (Path):** `/trust_score_logs/{logId}`
- **Ý nghĩa & Mục đích:** Lưu vết toàn bộ lịch sử biến động Điểm uy tín của khách hàng một cách minh bạch, phục vụ cơ chế chống gian lận và chính sách bắt buộc đặt cọc đơn hàng.
- **Các Actor truy cập:** Khách hàng (xem lịch sử điểm tại ProfileActivity), Quản trị viên (đối soát).
- **Cấu trúc trường dữ liệu (Schema):**
  - `logId` *(String)*: Mã bản ghi nhật ký.
  - `customerId` *(String)*: Mã khách hàng liên quan.
  - `orderId` *(String)*: Mã đơn hàng phát sinh sự kiện thay đổi điểm.
  - `changeAmount` *(Int)*: Số điểm thay đổi (ví dụ: `-10` điểm do hủy đơn COD, `+1` điểm do hoàn thành đơn).
  - `oldScore` *(Int)*: Điểm uy tín trước khi thay đổi.
  - `newScore` *(Int)*: Điểm uy tín sau khi thay đổi.
  - `reason` *(String)*: Lý do thay đổi điểm (ví dụ: "cancelled_unpaid_order", "completed_order").
  - `createdAt` *(Long)*: Thời điểm ghi nhận.
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "trust_score_logs": {
    "LOG_8821": {
      "logId": "LOG_8821",
      "customerId": "USR_CUST_001",
      "orderId": "ORD_1731801234",
      "changeAmount": -10,
      "oldScore": 100,
      "newScore": 90,
      "reason": "Hủy đơn hàng phương thức COD khi đang chế biến",
      "createdAt": 1731802000000
    }
  }
}
```

---

### 13. Node `system_config` — Cấu hình vận hành hệ thống toàn cục
- **Đường dẫn (Path):** `/system_config`
- **Ý nghĩa & Mục đích:** Trung tâm điều khiển toàn bộ tham số vận hành sàn của Quản trị viên. Khi Admin thay đổi giá trị tại đây, toàn bộ ứng dụng của Khách, Quán và Tài xế đều tự động cập nhật ngay lập tức mà không cần phát hành lại bản cập nhật ứng dụng.
- **Các Actor truy cập:** Admin (Toàn quyền chỉnh sửa), Khách & Quán & Tài xế (Đọc cấu hình).
- **Cấu trúc trường dữ liệu (Schema):**
  - `vietqrCountdownMinutes` *(Int)*: Thời gian đếm ngược thanh toán VietQR (mặc định: 15 phút).
  - `depositRequiredScoreThreshold` *(Int)*: Ngưỡng điểm uy tín bắt buộc đặt cọc (mặc định: `< 80` điểm).
  - `depositPercentage` *(Int)*: Tỷ lệ tiền cọc tối thiểu bắt buộc (mặc định: 30%).
  - `lockOrderScoreThreshold` *(Int)*: Ngưỡng điểm uy tín bị khóa đặt món (mặc định: `< 50` điểm).
  - `shippingRules` *(Node con)*: Bảng quy tắc tính giá cước vận chuyển theo khoảng cách:
    - Nội quận (dưới 3km): 15.000 VNĐ.
    - Liên quận (3km - 8km): 25.000 VNĐ.
    - Ngoại ô (trên 8km): 45.000 VNĐ + 5.000 VNĐ/km phát sinh.
  - `driverMultiStopMaxDistanceKm` *(Double)*: Khoảng cách tối đa cho phép ghép chuyến tài xế (mặc định: 1.5 km).
- **Mẫu dữ liệu thực tế (JSON Example):**
```json
{
  "system_config": {
    "vietqrCountdownMinutes": 15,
    "depositRequiredScoreThreshold": 80,
    "depositPercentage": 30,
    "lockOrderScoreThreshold": 50,
    "driverMultiStopMaxDistanceKm": 1.5,
    "shippingRules": {
      "INTRA_DISTRICT": {
        "ruleName": "Nội quận",
        "baseFee": 15000,
        "maxDistanceKm": 3.0
      },
      "INTER_DISTRICT": {
        "ruleName": "Liên quận",
        "baseFee": 25000,
        "maxDistanceKm": 8.0
      }
    }
  }
}
```

---

## BẢNG MA TRẬN PHÂN QUYỀN TRUY CẬP (CRUD) CÁC NODE THEO ACTORS

| Tên Node Firebase | Khách hàng (`CUSTOMER`) | Quán ăn (`MERCHANT`) | Tài xế (`DRIVER`) | Đầu bếp (`CHEF`) | Chủ chuỗi (`BOSS`) | Quản trị (`ADMIN`) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| `users` | Đọc / Sửa profile | Đọc profile | Đọc profile | Đọc profile | Đọc nhân viên | **Toàn quyền (Khóa/Mở)** |
| `customers` | **Đọc / Ghi sổ địa chỉ** | Đọc địa chỉ giao | Đọc địa chỉ giao | Không truy cập | Không truy cập | Đọc hồ sơ |
| `stores` | Đọc quán & menu | **Đọc / Sửa quán & menu** | Đọc địa chỉ lấy món | Đọc món | Đọc chuỗi quán | **Duyệt eKYC / Khóa quán** |
| `categories` | Đọc danh mục | Đọc danh mục | Không truy cập | Không truy cập | Đọc danh mục | **Thêm / Sửa / Xóa** |
| `carts` | **Toàn quyền giỏ hàng** | Không truy cập | Không truy cập | Không truy cập | Không truy cập | Không truy cập |
| `orders` | Tạo đơn, Hủy đơn, Theo dõi | Nhận đơn, Nấu, Đóng gói | Nhận cuốc, Giao, Quét QR | Đổi trạng thái nấu | Báo cáo doanh thu | **Toàn quyền điều phối/hủy** |
| `tracking` | Lắng nghe vị trí xe | Lắng nghe vị trí xe | **Cập nhật GPS liên tục** | Không truy cập | Không truy cập | Giám sát toàn sàn |
| `drivers` | Đọc thông tin shipper | Đọc thông tin shipper | **Bật/Tắt trực, nhận đơn** | Không truy cập | Không truy cập | Duyệt hồ sơ tài xế |
| `chefs` | Không truy cập | Quản lý đầu bếp quán | Không truy cập | **Nhận đơn KDS bếp** | Quản lý đầu bếp chuỗi | Quản trị |
| `merchants` | Không truy cập | **Đọc/Ghi cấu hình quán** | Không truy cập | Không truy cập | Quản lý thu ngân | Quản trị |
| `boss_stores` | Không truy cập | Đọc chi nhánh mình | Không truy cập | Không truy cập | **Toàn quyền chuỗi** | Quản trị |
| `trust_score_logs` | Xem lịch sử điểm của mình | Không truy cập | Không truy cập | Không truy cập | Không truy cập | **Đối soát / Khôi phục điểm** |
| `system_config` | Đọc phí ship & đếm ngược | Đọc cấu hình | Đọc cấu hình ghép cuốc | Không truy cập | Đọc cấu hình | **Toàn quyền thiết lập sàn** |

---

## TỔNG KẾT
Cấu trúc 13 nodes trên Firebase Realtime Database của dự án Oishi Food được phân tách trách nhiệm (Separation of Concerns) rất rõ ràng, đảm bảo tính toàn vẹn dữ liệu, hỗ trợ mở rộng không giới hạn và đáp ứng hoàn hảo yêu cầu khắt khe của một đồ án chuyên đề tốt nghiệp công nghệ di động đa phân hệ thực tế.
