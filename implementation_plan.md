# Kế hoạch Thực thi Hệ thống (Đã Sắp Xếp Theo Mức Độ Ưu Tiên)

Mục tiêu chung: Dọn dẹp giao diện, Phân quyền thông báo, Refactor kiến trúc Routing, Cải thiện UX và Bổ sung tính năng Tiến độ nhóm.

---

## 0. Nguyên Tắc Thực Thi Bắt Buộc (Execution Rules)

> [!CAUTION]
> **Đây là kim chỉ nam cho toàn bộ quá trình phát triển:**
> 1. **Nghiên cứu kỹ càng trước khi hành động:** Tuyệt đối không vội vàng sửa code khi chưa nắm rõ luồng dữ liệu (data flow) từ Frontend xuống Backend.
> 2. **Đánh giá tác động chéo (Impact Analysis):** Phải search toàn bộ project xem các component/API đang bị tác động được dùng ở đâu trước khi sửa/xoá.
> 3. **Quy chuẩn Ngôn ngữ (Language Standard):** Mọi văn bản **hiển thị lên giao diện (UI) bắt buộc 100% bằng Tiếng Việt**. Mọi biến, tên hàm, cấu trúc **code bên dưới bắt buộc bằng Tiếng Anh** theo đúng tiêu chuẩn.
> 4. **Code sạch & Chuẩn mực (Clean Code):** Viết code có quy chuẩn, format gọn gàng. Tên biến, tên hàm bằng tiếng Anh phải có ý nghĩa minh bạch. Không viết code tắt, lộn xộn.
> 5. **Đảm bảo hoạt động đúng yêu cầu:** Mọi chức năng sau khi làm xong phải đáp ứng chính xác 100% mục tiêu ban đầu.
> 6. **Dọn rác & Test vòng lặp (Clean & Re-test):** BẮT BUỘC phải dọn dẹp comment vô nghĩa, biến không dùng, file dư thừa sau khi code chạy đúng. Sau khi dọn dẹp xong phải chạy Test lại một lần nữa.
> 7. **Kiểm thử toàn diện (Exhaustive Testing):** Không chỉ test luồng cốt lõi (happy paths) mà phải giả lập cả ngoại lệ dữ liệu, đảm bảo bao phủ mọi kịch bản.

---

## 1. Giai Đoạn 1: Cấu Trúc Nền Tảng (Ưu Tiên Cao Nhất)

Việc cấu trúc lại hệ thống Frontend cần phải được làm đầu tiên, bởi vì mọi giao diện và component khác đều sống trên bộ khung này. Nếu làm sau cùng sẽ phá vỡ mọi bản vá UI đã làm trước đó.

### 1.1 Refactor Kiến trúc Routing và Layout
- **[NEW] `frontend/src/layouts/AppShell.tsx`**: Trích xuất component layout độc lập. Sử dụng `<Outlet />` của React Router. Xóa các nút "Về Thư viện màn hình".
- **[MODIFY] `frontend/src/routes/index.tsx`**: Thay thế biến toàn cục bằng cấu trúc React Router lồng nhau (nested routes). Xóa bỏ hoàn toàn route `/gallery`.
- **[DELETE] `frontend/src/pages/Gallery/GalleryPage.tsx`**: Xóa file ra khỏi hệ thống hoàn toàn sau khi đã di dời layout.
- **[MODIFY] Các màn hình Player / Auth**: Xóa bỏ nút "Quay lại Thư viện màn hình" và mọi logic liên đới.

### 1.2 Phân quyền Thông báo (Hệ thống cốt lõi)
- **Backend:** Cập nhật `NotificationController`, `NotificationService`, `NotificationRepository` để map tham số `role` sang danh sách `NotificationType` tương ứng và thực hiện query/lọc sự kiện SSE.
- **Frontend:** Truyền `?role=...` vào các API lấy thông báo và kết nối Realtime SSE tại `notification-service.ts`, `NotificationsPage.tsx`, `AppShell.tsx`.
- **(Rule)** Các Role phân loại như sau:
  - **Học viên:** Tương tác diễn đàn, Học tập (Quiz, Assignment), Chứng chỉ, Ghi danh, Nhóm học, Thông báo hệ thống.
  - **Giảng viên:** Tương tác diễn đàn, Học viên nộp bài, Duyệt khóa học, Tài liệu AI, Thông báo hệ thống.
  - **Quản trị viên:** Yêu cầu duyệt khóa học, Thông báo hệ thống.

---

## 2. Giai Đoạn 2: Xây Dựng Tính Năng (Ưu Tiên Trung Bình)

Sau khi nền tảng layout và thông báo đã vững chắc, ta tiến hành nâng cấp các tính năng tác động đến cơ sở dữ liệu và DTO.

### 2.1 Tính năng Tiến độ nhóm (Instructor Group)
- **Backend (`GroupMemberResponse.java`):** Thêm trường `Integer progress`.
- **Backend (`GroupServiceImpl.java`):** Query dữ liệu tiến độ từ `CourseProgressRepository` theo `courseId` và `studentId`, map vào danh sách trả về.
- **Frontend (`InsGroupDetail.tsx`):** Hiển thị cột "Tiến độ" ở bảng thành viên. Tính toán trung bình cộng `progress` của cả nhóm và gán vào thẻ `<StatCard label="Tiến độ TB" />`.

---

## 3. Giai Đoạn 3: Cải Tiến & Dọn Dẹp Giao Diện (Ưu Tiên Thấp)

Những chỉnh sửa về mặt hình ảnh (visual) ít rủi ro hệ thống nhất, sẽ được thực hiện sau cùng trên bộ layout mới.

### 3.1 Nâng cấp Component và Sửa lỗi UI
- **[MODIFY] `components/base/index.tsx`:** Thiết kế lại Custom Component `Select` (Dropdown Filter) để có width 100%, text ellipsis và hiện tooltip.
- **[MODIFY] `AppShell.tsx`:** Sửa mapping alias để sidebar mục "Chứng chỉ" ở Admin active đúng màu.
- **[MODIFY] `AdminCertificates.tsx`:** Đưa thẻ `<window.PageBar />` ra ngoài card bọc table.

### 3.2 UI Cleanup (Dọn dẹp Header, Auth, Settings)
- **[MODIFY] `AppShell.tsx`:** Xóa thanh tìm kiếm toàn cục, Xóa nút Trợ giúp (?), Xóa Dropdown tài khoản (chỉ để Avatar và tên tĩnh).
- **[MODIFY] `SettingsPage.tsx`:** Xóa tab "Thông báo" và "Tùy chọn".
- **[MODIFY] `LoginPage.tsx`:** Xóa bỏ logo "Rikkei Edu" góc trái. Xóa bỏ dòng text "Hỗ trợ kỹ thuật · Chính sách bảo mật" dưới footer.

---

## 4. Giai Đoạn 4: System Audit & Bảo Mật (Xuyên Suốt Quá Trình Code)

- **Security (Phòng vệ IDOR & @PreAuthorize):** Bắt buộc sử dụng `@PreAuthorize` tại Controller Level cho tất cả API có chứa thông tin định danh (ID) để phòng vệ lỗ hổng IDOR. Ví dụ: Đảm bảo học viên này không thể sửa URL để xem lén dữ liệu của học viên khác.
- **Security & Data Trimming:** 
  - Chống SQL Injection (kiểm tra `Specification` và custom queries).
  - Tối ưu DTOs: Dọn sạch các trường dữ liệu thừa, không bao giờ gửi password hay PII không cần thiết (dùng `@JsonIgnore`).
  - Ngừa XSS ở Frontend bằng chức năng sanitize tự động của React.
- **Performance:** Tối ưu hóa Database (dùng mệnh đề IN, tránh N+1), tối ưu vòng lặp render ở kiến trúc React Router mới.
- **Code Refactoring:** Xóa sạch các biến `unused`, dọn file thừa thãi sau khi tính năng đã chạy.

---

## 5. Giai Đoạn 5: Final QA & Iterative Test-Fix-Test (Kiểm Thử Cuối Cùng)

Đây là công đoạn KIỂM TRA CUỐI CÙNG theo chu trình **Iterative (Test toàn diện -> Phát hiện thiếu sót -> Fix -> Dọn Code Thừa -> Test lại)**.

### 5.1 Bổ sung logic bị sót (Missing Logic Patching)
- Trong lúc chạy test, bất kỳ thao tác nào thiếu bắn sự kiện Notification (như tạo nhóm, gán thành viên) hay thiếu render Realtime đều sẽ được vá code ngay lập tức.

### 5.2 Kiểm thử Tất Cả Các Luồng (Exhaustive E2E Testing)
1. **Luồng Auth (Bảo mật JWT):** Login/Logout 3 roles. Đảm bảo token hết hạn hoặc xóa sạch. Test cố tình truy cập link cấm xem có văng ra đúng trang Login không.
2. **Luồng Routing (Mọi Ngóc Ngách):** Bấm toàn bộ Sidebar, Back/Forward. Resize màn hình xem responsive có vỡ không sau khi thay layout.
3. **Luồng Instructor Groups (Ngoại Lệ):** Tạo nhóm trống, tạo nhóm toàn người tiến độ 0%, gỡ thành viên khỏi nhóm xem "Tiến độ TB" có tính lại ngay lập tức không.
4. **Luồng Notification (Phân quyền):** Bắn nhiều loại thông báo khác nhau, kể cả thông báo không xác định, kiểm tra xem SSE có bị đứt kết nối hay rò rỉ bộ nhớ (memory leak) trên frontend không.
5. **Luồng UI/UX:** Test Sidebar Admin Chứng chỉ, Phân trang ở nhiều kích thước màn hình. Filter box hoạt động tốt kể cả khi zoom in/out. Đảm bảo toàn app sử dụng chung một ngôn ngữ đồng nhất.
