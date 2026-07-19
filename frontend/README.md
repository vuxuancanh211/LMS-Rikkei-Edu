# LMS Rikkei Edu — React + TypeScript (Vite)

Hệ thống Quản lý Học tập (Learning Management System) cho Rikkei Edu, dựng bằng **React 18 + TypeScript + Vite**.
Giao diện **responsive đầy đủ** cho Desktop / Tablet / Mobile, 3 vai trò: **Học viên · Giảng viên · Quản trị viên**.

---

## 1. Yêu cầu

- **Node.js ≥ 18** (khuyến nghị 18 hoặc 20). Kiểm tra: `node -v`
- npm (đi kèm Node) hoặc yarn / pnpm

## 2. Cài đặt & chạy (development)

```bash
# 1) Mở thư mục dự án trong terminal
cd lms-rikkei-edu

# 2) Cài thư viện
npm install

# 3) Chạy server phát triển (tự mở http://localhost:5173)
npm run dev
```

Trình duyệt sẽ mở **Thư viện màn hình** — danh sách **tất cả màn hình & popup** của dự án (33 mục, chia 7 nhóm: Xác thực · Học viên · Giảng viên · Quản trị · Dùng chung · Toàn màn hình · Popup). Bấm vào một thẻ để mở màn hình đó **full màn hình, tương tác thật và responsive**; bấm **"Thư viện"** (góc trên trái / sidebar) để quay lại danh sách.

Trong mỗi màn hình bạn vẫn: đổi vai trò (Học viên / Giảng viên / Quản trị) ở đầu sidebar, điều hướng giữa các trang, và mở mọi popup bằng cách bấm các nút bên trong.

## 3. Build bản production

```bash
npm run build      # xuất ra thư mục dist/
npm run preview    # xem thử bản build tại http://localhost:4173
npm run typecheck  # (tùy chọn) kiểm tra kiểu TypeScript với tsc --noEmit
```

Thư mục `dist/` là bản tĩnh, có thể deploy lên bất kỳ hosting nào (Netlify, Vercel, Nginx, GitHub Pages…).

> Lưu ý: `npm run build` dùng Vite (esbuild) nên **luôn build được** kể cả khi còn cảnh báo kiểu. Dùng `npm run typecheck` khi muốn siết kiểu dần.

---

## 4. Cấu trúc thư mục

```
lms-rikkei-edu/
├─ index.html                # HTML gốc của Vite (nạp /src/main.tsx)
├─ package.json              # scripts + dependencies (React 18 + TypeScript + Vite)
├─ tsconfig.json             # cấu hình TypeScript (app)
├─ tsconfig.node.json        # cấu hình TypeScript cho vite.config.ts
├─ vite.config.ts            # cấu hình Vite + plugin React
├─ public/
│  └─ assets/courses/        # ảnh thumbnail khóa học (tĩnh)
└─ src/
   ├─ main.tsx               # điểm vào: nạp CSS + tất cả module (mỗi màn 1 file)
   ├─ globals.ts             # expose React/ReactDOM ra global cho các module
   ├─ global.d.ts            # khai báo type cho các global (window.*, React UMD)
   ├─ vite-env.d.ts          # type cho môi trường Vite
   ├─ mount.tsx              # THƯ VIỆN MÀN HÌNH + shell (đổi vai trò/điều hướng/popup)
   ├─ styles/
   │  ├─ tokens.css          # design tokens (màu, typography, spacing, components)
   │  └─ layout.css          # layout shell + RESPONSIVE (mobile/tablet/desktop) + gallery
   ├─ data.ts                # dữ liệu mẫu (khóa học, người dùng, bài tập…)
   ├─ icons.tsx              # bộ icon (SVG, stroke)
   ├─ components.tsx         # thư viện UI dùng chung (Card, Chip, Table, Charts, Modal, phân trang…)
   ├─ login.tsx              # màn Đăng nhập (split layout)
   ├─ ai-chatbot.tsx         # nút + cửa sổ Trợ lý AI (góc dưới phải)
   ├─ chat.tsx               # Chat nhóm (dùng chung GV & HV)
   ├─ settings.tsx           # Cài đặt tài khoản
   └─ screens/               # ⭐ MỖI MÀN HÌNH = 1 FILE (popup của màn nào nằm trong file đó)
      ├─ StuDashboard.tsx        · Học viên — Tổng quan
      ├─ StuCourses.tsx          · Học viên — Khóa học của tôi
      ├─ StuTasks.tsx            · Học viên — Bài tập & Quiz (+ popup nộp bài)
      ├─ StuForum.tsx            · Diễn đàn (+ popup tạo bài, trang chi tiết & bình luận)
      ├─ StuCerts.tsx            · Học viên — Chứng chỉ
      ├─ InsDashboard.tsx        · Giảng viên — Tổng quan
      ├─ InsCourses.tsx          · Giảng viên — Khóa học (+ popup Tạo khóa học)
      ├─ InsCourseDetail.tsx     · Giảng viên — Chi tiết khóa học (+ popup thêm video/tài liệu)
      ├─ InsGroups.tsx           · Giảng viên — Quản lý nhóm (+ popup tạo nhóm)
      ├─ InsGroupDetail.tsx      · Giảng viên — Chi tiết nhóm
      ├─ InsAssess.tsx           · Giảng viên — Bài tập & Trắc nghiệm (+ popup thêm câu hỏi/tạo đề)
      ├─ InsGrading.tsx          · Giảng viên — Chấm điểm (+ popup chấm điểm & nhận xét)
      ├─ InsStudents.tsx         · Giảng viên — Học viên
      ├─ AdminDashboard.tsx      · Quản trị — Tổng quan hệ thống
      ├─ AdminUsers.tsx          · Quản trị — Người dùng (+ popup thêm người dùng)
      ├─ AdminCourses.tsx        · Quản trị — Khóa học
      ├─ AdminApproval.tsx       · Quản trị — Phê duyệt (+ popup chi tiết/xem trước/từ chối)
      ├─ AdminReports.tsx        · Quản trị — Báo cáo & Thống kê
      ├─ AdminLogs.tsx           · Quản trị — Nhật ký hệ thống
      ├─ LecturePlayer.tsx       · Toàn màn hình — Trình phát bài giảng (+ AI Chatbot)
      ├─ QuizPlayer.tsx          · Toàn màn hình — Làm Quiz (+ Proctoring)
      └─ QuizResult.tsx          · Toàn màn hình — Kết quả bài kiểm tra
```

## 5. Danh sách màn hình (33 mục)

- **Xác thực (1):** Đăng nhập
- **Học viên (5):** Tổng quan · Khóa học của tôi · Bài tập & Quiz · Chứng chỉ · Cài đặt
- **Giảng viên (8):** Tổng quan · Quản lý Khóa học · Chi tiết Khóa học · Quản lý Nhóm · Chi tiết Nhóm · Bài tập & Trắc nghiệm · Chấm điểm · Học viên
- **Quản trị (6):** Tổng quan hệ thống · Quản lý Người dùng · Quản lý Khóa học · Phê duyệt · Báo cáo · Nhật ký
- **Dùng chung GV & HV (2):** Diễn đàn · Chat nhóm
- **Toàn màn hình (3):** Trình phát bài giảng (+AI) · Làm Quiz (Proctoring) · Kết quả
- **Popup / Cửa sổ (8):** Tạo khóa học · Thêm câu hỏi · Tạo trắc nghiệm ngẫu nhiên · Ngân hàng câu hỏi · Chi tiết phê duyệt · Xem trước Video · Tạo bài đăng · Chi tiết bài đăng (bình luận)

> Các popup khác (nộp bài, chấm điểm, thêm người dùng, từ chối duyệt…) mở được ngay bên trong màn hình tương ứng bằng cách bấm nút.

## 6. Responsive

Toàn bộ responsive nằm trong `src/styles/layout.css` (các `@media`):

| Thiết bị | Bề rộng | Hành vi |
|---|---|---|
| **Desktop** | > 1024px | Sidebar đầy đủ, lưới thẻ 4–5 cột |
| **Tablet**  | 768–1024px | Sidebar thu thành thanh icon (rail), lưới 2 cột |
| **Mobile**  | < 768px | Sidebar thành ngăn kéo + nút hamburger, lưới 1 cột |

Sidebar và thanh trên cùng (topbar) cố định; chỉ vùng nội dung cuộn.

## 7. Ghi chú kỹ thuật

- Các module trong `src/` được viết theo phong cách **đăng ký vào `window`** (ví dụ `window.StuDashboard`). **Mỗi màn hình là  1 file** trong `src/screens/`, popup của màn nào nằm ngay trong file đó. `globals.ts` đưa `React`/`ReactDOM` ra global; `main.tsx` nạp các module **đúng thứ tự phụ thuộc** rồi `mount.tsx` dựng Thư viện màn hình. Đây là cách giữ nguyên 100% mã giao diện đã kiểm thử.
- **TypeScript:** type cho các global (`window.*`, `React`) được khai báo trong `src/global.d.ts`; `tsconfig.json` đặt kiểu **nơi lỏng** (`strict: false`, `noImplicitAny: false`, `allowUmdGlobalAccess`) để dễ chuyển dần. Bạn có thể siết `strict: true` và bổ sung type cho từng component theo thời gian.
- Khi thêm 1 màn hình mới: tạo file `.tsx` trong `src/screens/`, đăng ký `window.TênMàn = TênMàn;`, thêm dòng `import './screens/TênMàn';` vào `main.tsx`, và (nếu muốn hiện trong Thư viện) thêm 1 mục vào mảng `CATALOG` trong `mount.tsx`.
- Dữ liệu hiện là **mock tĩnh** trong `data.ts`. Khi nối API thật (Java Spring Boot theo SRS), thay các mảng trong `data.ts` bằng lời gọi API (fetch/axios) và truyền xuống component.
- Không phụ thuộc thư viện UI ngoài — chỉ React. Icon và biểu đồ đều là SVG tự vẽ.

---

© 2026 Rikkei Edu. Giao diện mẫu phục vụ phát triển.
