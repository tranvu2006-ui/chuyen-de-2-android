# Cẩm Nang Tối Ưu Hóa Token & Cửa Sổ Ngữ Cảnh (Token & Context Optimization Guide)

> **Mục tiêu**: Giảm thiểu từ **70% - 95%** lượng token tiêu thụ mỗi phiên làm việc, tăng tốc độ phản hồi của AI Assistant, ngăn ngừa hiện tượng tràn context (buộc phải nén/compaction sớm) và nâng cao độ chính xác khi lập trình cùng AI (Antigravity, Cursor, Copilot, Claude Code).

---

## 1. Bản Chất Vấn Đề & "Cái Bẫy" Hao Phí Token

Trong các mô hình AI Coding (LLM):
- **Input Tokens**: Toàn bộ lịch sử hội thoại, nội dung các file đọc vào, output dòng lệnh terminal, và prompt của bạn.
- **Output Tokens**: Nội dung câu trả lời và code do AI sinh ra.
- **Giới hạn Context Window**: Khi tổng token đạt trần (100k - 200k+ tokens), hệ thống buộc phải nén ngữ cảnh (**Compaction**), khiến AI dễ quên mất các chi tiết kiến trúc ban đầu.

### ⚠️ Top 3 nguyên nhân gây tràn Token nhanh nhất trong dự án Android:
1. **Dán thô Logcat / Gradle Build Log**: Một lần dán logcat chưa lọc có thể nuốt trọn **20,000 – 100,000+ tokens** ngay lập tức.
2. **Ghi đè lại toàn bộ file code (Full File Rewrite)**: AI in lại file 500 – 1,000 dòng chỉ để sửa 2 dòng code.
3. **Đọc toàn bộ file không giới hạn dòng**: AI đọc hàng chục file 800 dòng thay vì chỉ đọc đúng hàm cần sửa.

---

## 2. Quy Tắc Dành Cho Người Dùng (Prompt Engineering Tiết Kiệm Token)

### 2.1. Lọc Log Trước Khi Gửi Vào Chat
| Tình Huống | ❌ Cách Làm Tốn Token (Tránh Dùng) | ✅ Cách Tối Ưu Token (Nên Dùng) |
| :--- | :--- | :--- |
| **Báo lỗi Crash App** | Copy toàn bộ màn hình Logcat hàng ngàn dòng (bao gồm log hệ thống MIUI, SurfaceFlinger, PerfHAL...). | Chỉ copy đúng đoạn **`FATAL EXCEPTION`** và **Stacktrace** liên quan đến package ứng dụng (`com.chuyen_de_2...`). |
| **Lọc bằng lệnh ADB** | `adb logcat` (in vô tận) | `adb logcat -d -s AndroidRuntime:E MyTag:E` hoặc `adb logcat *:E \| grep -E "FATAL\|com.chuyen_de_2"` |
| **Lỗi Gradle Build** | Copy toàn bộ log từ lúc tải dependency đến khi build. | Chỉ copy đoạn block **`FAILURE: Build failed with an exception`** và các dòng `e: ...` màu đỏ. |

### 2.2. Yêu Cầu AI Trả Lời Ngắn Gọn & Sửa Code Dạng Chunk
- ❌ **Kém hiệu quả**: *"Hãy phân tích file HomeActivity và viết lại toàn bộ file giúp tôi để thêm chức năng giỏ hàng."* (Tốn ~4,000 output tokens).
- ✅ **Chuẩn tối ưu**: *"Thêm hàm `observeCart()` vào `HomeActivity.kt`. Chỉ trả lời bằng snippet thay đổi hoặc diff ngắn gọn, không viết lại toàn bộ file."* (Tốn ~250 tokens).

### 2.3. Cung Cấp Ngữ Cảnh Có Trọng Tâm
- Chỉ dẫn rõ tên file, tên class hoặc tên hàm cần xử lý:
  - *"Sửa lỗi crash khi click đặt hàng tại hàm `handleCheckout` trong `CheckoutActivity.kt`."*

---

## 3. Quy Tắc Dành Cho AI Assistant (Agentic Tool Optimization)

Khi AI thao tác với codebase qua các Tools, cần tuân thủ nghiêm ngặt các nguyên tắc sau:

### 3.1. Đọc File Có Giới Hạn (`view_file` với `StartLine` & `EndLine`)
- **Tuyệt đối không** đọc full file 800 dòng nếu chỉ muốn xem hàm xử lý logic hoặc khai báo biến.
- **Quy trình chuẩn**:
  1. Dùng `grep_search` để định vị chính xác số dòng của hàm/class cần sửa.
  2. Dùng `view_file` kèm tham số `StartLine: 45, EndLine: 95` (chỉ xem 50 dòng cần thiết).
  - *Hiệu quả: Tiết kiệm ~1,500 token/lần đọc.*

### 3.2. Sửa Code Bằng Chunk (`replace_file_content` & `multi_replace_file_content`)
- Luôn sử dụng cơ chế **Target Content & Replacement Content** cục bộ.
- Giữ phạm vi thay đổi nhỏ nhất có thể (chỉ vài dòng đến vài chục dòng xung quanh vị trí cần chỉnh sửa).
- Không bao giờ dùng `write_to_file` để ghi đè toàn bộ file đã tồn tại nếu chỉ chỉnh sửa một phần.

### 3.3. Tìm Kiếm Thông Minh (`grep_search` & `list_dir`)
- Luôn loại trừ các thư mục rác hoặc thư mục build:
  - `Includes`: `["*.kt", "*.xml", "*.gradle.kts"]`
  - Loại trừ: `build/`, `.gradle/`, `.idea/`, `captures/`, `node_modules/`.
- Sử dụng `MatchPerLine: false` khi chỉ cần lấy danh sách các file có chứa từ khóa thay vì in toàn bộ các dòng.

### 3.4. Giới Hạn Output Của Lệnh Terminal (`run_command`)
- Tránh chạy các lệnh dump toàn bộ log dài.
- Áp dụng các cờ rút gọn:
  - Gradle: `.\gradlew compileDebugSources --quiet` hoặc chỉ grep task lỗi.
  - Git: `git log -n 5 --oneline` thay vì `git log`.
  - Git Diff: `git diff --stat` trước khi xem diff chi tiết từng file.
- **Không Polling liên tục**: Tận dụng cơ chế wakeup sự kiện tự động thay vì liên tục gọi `manage_task(status)` trong vòng lặp.

---

## 4. Bảng Định Lượng Tỷ Lệ Tiết Kiệm Token

| Tác Vụ | Lượng Token Thông Thường | Lượng Token Sau Tối Ưu | Mức Độ Tiết Kiệm |
| :--- | :---: | :---: | :---: |
| **Trích xuất lỗi Logcat** | 15,000 – 60,000 tokens | 300 – 800 tokens | **Giảm 95% – 98%** |
| **Sửa đổi logic trong Activity/Service** | 3,000 – 8,000 tokens | 150 – 400 tokens | **Giảm 90% – 95%** |
| **Đọc cấu trúc file code lớn** | 2,000 – 4,000 tokens | 100 – 300 tokens | **Giảm 85% – 92%** |
| **Kiểm tra trạng thái build Gradle** | 5,000 – 15,000 tokens | 200 – 600 tokens | **Giảm 90% – 96%** |
| **Trả lời chat & giải thích** | 1,000 – 2,500 tokens | 150 – 350 tokens | **Giảm 75% – 85%** |

---

## 5. File Cấu Hình Mẫu Tiết Kiệm Token Tự Động (`GEMINI.md` / `.cursorrules`)

Bạn có thể lưu nội dung sau vào file **`GEMINI.md`** hoặc **`.cursorrules`** tại thư mục gốc của project để hệ thống AI luôn kích hoạt chế độ tiết kiệm token:

```markdown
# TOKEN & CONTEXT EFFICIENCY DIRECTIVE

## 1. OUTPUT CONCISENESS
- Provide direct, concise answers without pleasantries, conversational filler, or boilerplate introductions.
- When fixing code, provide ONLY the targeted diff or replacement chunk. NEVER rewrite the entire file unless explicitly instructed.
- Do not repeat or re-summarize content already documented in artifacts or files.

## 2. TOOL PRECISION
- Restrict `view_file` to specific line ranges (`StartLine`/`EndLine`) around target symbols.
- In `grep_search`, filter out `build/`, `.gradle/`, `.idea/`, `bin/`, `out/`.
- In terminal commands, pipe or limit verbose output (e.g., use `--quiet`, `--stacktrace` only when needed, `git log -n 5`).

## 3. ERROR & LOG EXTRACTION
- When inspecting logs or Logcat, isolate the specific root cause and stacktrace. Ignore normal system lifecycle logs (MIUI, SurfaceFlinger, HAL).
```

---

## 6. Check-list Nhanh Trước Khi Gửi Yêu Cầu (Quick Checklist)

- [ ] Bạn đã cắt gọn logcat/build log, chỉ giữ lại đoạn Exception chính chưa?
- [ ] Bạn đã chỉ định rõ tên file và chức năng cần sửa chưa?
- [ ] Yêu cầu của bạn có nêu rõ "chỉ trả về phần sửa đổi" chưa?
- [ ] Bạn đã tránh copy toàn bộ cả file code vào ô chat chưa (hãy dùng `@ten_file` hoặc đường dẫn file để AI tự đọc)?
