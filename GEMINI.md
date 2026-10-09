# WORKSPACE RULES & TOKEN OPTIMIZATION DIRECTIVE
# Scope: e:\Android\Chuyen-De-2 (Áp dụng riêng cho dự án Chuyên đề 2 - Oishi Food)

---

## 1. PHẠM VI & MỤC TIÊU (SCOPE & GOALS)
- Các quy tắc dưới đây chỉ áp dụng cho workspace `e:\Android\Chuyen-De-2`.
- Mục tiêu: Giảm thiểu tiêu thụ Token, tăng tốc độ phản hồi của AI, ngăn ngừa tràn Context Window (tránh nén ngữ cảnh sớm), và đảm bảo code chất lượng cao.

---

## 2. QUY TẮC PHẢN HỒI (OUTPUT CONCISENESS)
- **Trả lời súc tích, đi thẳng vào vấn đề**: Không chào hỏi rườm rà, không thêm đoạn mở đầu/kết thúc xã giao.
- **Sửa code dạng Diff / Partial Chunk**: Khi chỉnh sửa code, CHỈ đưa ra đoạn code thay đổi hoặc dùng tool sửa code cục bộ. Tuyệt đối KHÔNG viết lại toàn bộ file nếu file chỉ sửa một vài vị trí.
- **Không tóm tắt lại Artifacts**: Không tóm tắt nội dung tài liệu vừa tạo/sửa trong tin nhắn chat nếu tài liệu đã được ghi ra file.

---

## 3. QUY TẮC THAO TÁC CÔNG CỤ (TOOL OPTIMIZATION)
- **Đọc file có giới hạn (`view_file`)**:
  - Không đọc toàn bộ file lớn (>150 dòng) nếu chỉ cần sửa 1 hàm.
  - Luôn chỉ định `StartLine` và `EndLine` cụ thể.
- **Tìm kiếm có bộ lọc (`grep_search`)**:
  - Luôn lọc theo phần mở rộng (`*.kt`, `*.xml`, `*.gradle.kts`).
  - Loại trừ các thư mục build: `build/`, `.gradle/`, `.idea/`, `captures/`, `dist/`.
  - Sử dụng `MatchPerLine: false` khi chỉ cần tìm danh sách tên file.
- **Giới hạn Output dòng lệnh (`run_command`)**:
  - Khi chạy lệnh Gradle hoặc Git, giới hạn đầu ra tối thiểu (dùng `--quiet`, `git log -n 5`, v.v.).
  - Khi xem lỗi Logcat/Crash, chỉ trích xuất đúng khối `FATAL EXCEPTION` và stacktrace thuộc package `com.chuyen_de_2...`. Bỏ qua toàn bộ log hệ thống MIUI, SurfaceFlinger, PerfHAL.

---

## 4. QUY TẮC KIẾN TRÚC DỰ ÁN (PROJECT CONTEXT)
- **Kiến trúc 2 Ứng dụng (Multi-module)**:
  - `app-customer`: App dành cho Khách hàng (`HomeActivity`, xem quán, đặt món, giỏ hàng).
  - `app-internal`: App dành cho Quán ăn (`OrderListFragment` - Kanban) và Shipper (`ShipperFragment` - GPS).
  - `core`: Thư viện dùng chung (Models, Firebase Repositories, Utils).
- **Phân quyền 4 Roles**: `CUSTOMER`, `MERCHANT` (hoặc `MANAGER`), `DRIVER` (hoặc `SHIPPER`), `ADMIN`.
