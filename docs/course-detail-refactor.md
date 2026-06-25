# Course Detail & Player — Tài liệu kỹ thuật

## Mục lục

1. [Tách file InsCourseDetail](#1-tách-file-inscoursedetail)
2. [Tab Thông tin khóa học](#2-tab-thông-tin-khóa-học)
3. [Hero card & Thumbnail modal](#3-hero-card--thumbnail-modal)
4. [PreviewPlayer — Split view](#4-previewplayer--split-view)
5. [Admin Approval — diff fix](#5-admin-approval--diff-fix)
6. [Rút gửi duyệt](#6-rút-gửi-duyệt)

---

## 1. Tách file InsCourseDetail

### Vấn đề
`InsCourseDetail.tsx` đã vượt 2269 dòng, khó maintain.

### Giải pháp
Tách thành 3 file con theo pattern **IIFE + window** của dự án:

```
frontend/src/pages/Instructor/course-detail/
├── CourseModals.tsx        (~590 dòng)
├── CourseContentTab.tsx    (~272 dòng)
├── CourseVersionsTab.tsx   (~284 dòng)
└── (InsCourseDetail.tsx giảm còn ~1234 dòng)
```

### Pattern IIFE

Mỗi file là một IIFE tự đăng ký component vào `window`:

```ts
// @ts-nocheck
(function () {
  const { useState, useEffect } = React;
  const Ic = window.Icon;

  function MyComponent(props) { ... }

  Object.assign(window, { MyComponent });
})();
```

Trong `InsCourseDetail.tsx` đọc lại từ window:

```ts
const { AddChapterModal, AddLessonModal, CourseContentTab, CourseVersionsTab } = window;
const EditResourceInner = window.EditResourceModal;
```

### Đăng ký side-effect import

`frontend/src/providers/register-modules.ts` — thứ tự import quan trọng:

```ts
import '../pages/Instructor/course-detail/CourseModals';
import '../pages/Instructor/course-detail/CourseContentTab';
import '../pages/Instructor/course-detail/CourseVersionsTab';
import '../pages/Instructor/InsCourseDetail';
```

### Các component đã tách

| File | Exports |
|---|---|
| `CourseModals.tsx` | `AddChapterModal`, `AddLessonModal`, `AddResourceModal`, `EditResourceModal`, `ResourcePreviewModal` |
| `CourseContentTab.tsx` | `CourseContentTab` |
| `CourseVersionsTab.tsx` | `CourseVersionsTab`, `CourseHistoryTab` |

---

## 2. Tab Thông tin khóa học

### Thay đổi luồng

**Trước:** Thông tin cơ bản (title, description, level, category) phải qua flow draft → duyệt như nội dung khóa học.

**Sau:** Thông tin cơ bản được lưu trực tiếp, không cần duyệt.

### Phân quyền chỉnh sửa

```ts
// Nội dung (chapters, lessons, resources)
const canEdit = ["DRAFT", "REJECTED", "PUBLISHED"].includes(course.status);

// Thông tin cơ bản
const canEditInfo = course.status !== "ARCHIVED" && course.status != null;
```

### Backend — updateCourse

`CourseServiceImpl.java` — bỏ phân nhánh `isLive`, áp dụng thay đổi trực tiếp:

```java
@Override
public CourseResponse updateCourse(UUID instructorId, UUID courseId, UpdateCourseRequest request) {
    Course course = loadOwnedCourse(instructorId, courseId);
    if (course.getStatus() == CourseStatus.ARCHIVED)
        throw new CourseStateException("Cannot modify an archived course");

    if (request.getTitle() != null)        { course.setTitle(request.getTitle()); course.setSlug(...); }
    if (request.getDescription() != null)  course.setDescription(request.getDescription());
    if (request.getLevel() != null)        course.setLevel(request.getLevel());
    if (request.getCategoryId() != null)   course.setCategory(resolveCategory(request.getCategoryId()));
    if (request.getThumbnailUrl() != null) course.setThumbnailUrl(request.getThumbnailUrl());
    if (request.getChatEnabled() != null)  course.setChatEnabled(request.getChatEnabled());

    return courseMapper.toResponse(courseRepository.save(course));
}
```

### Fix: Category không thể chọn null

`Select` component luôn trả `string` từ `e.target.value`, kể cả khi value là `null`:

```tsx
// Sai — "null" string lọt qua
onChange={setEditCat}

// Đúng
onChange={v => setEditCat(v === "null" ? null : v)}
```

Và khi gửi API:

```ts
categoryId: (editCat && editCat !== "null") ? editCat : null,
```

---

## 3. Hero card & Thumbnail modal

### Hero card

Banner thumbnail đầy trang + overlay gradient + thông tin khóa học + chip trạng thái:

```
┌─────────────────────────────────────────────┐
│  [Thumbnail ảnh bìa]                        │
│  ░░░░░░░░░░░░░░░░░░░ gradient overlay ░░░░░ │
│                                             │
│  [Chip: PUBLISHED]  [Chip: INTERMEDIATE]    │
│  Tên khóa học                               │
│  ★ 0  |  0 bài  |  0 học viên              │
│                              [Đổi ảnh bìa] │
└─────────────────────────────────────────────┘
```

### Thumbnail modal

Luồng đổi ảnh bìa tách biệt khỏi form thông tin:

1. Instructor nhấn "Đổi ảnh bìa" → mở modal
2. Modal hiển thị ảnh hiện tại, cho phép chọn file mới
3. Preview file mới trong modal
4. Xác nhận → upload lên S3 qua presign → `PUT /instructor/courses/{id}` với `thumbnailUrl`
5. Progress bar overlay trên hero banner trong lúc upload

```ts
// States thumbnail modal
const [thumbModalOpen, setThumbModalOpen] = useState(false);
const [thumbModalFile, setThumbModalFile] = useState(null);
const [thumbModalPreview, setThumbModalPreview] = useState(null);
const [thumbUploading, setThumbUploading] = useState(false);
const [thumbProgress, setThumbProgress] = useState(0);
```

Upload flow:

```ts
// 1. Lấy presigned URL
const { data: presign } = await api.post(`/instructor/courses/${courseId}/presign-thumbnail`, {
  filename: file.name, contentType: file.type
});

// 2. PUT trực tiếp lên S3 với XHR để track progress
const xhr = new XMLHttpRequest();
xhr.upload.onprogress = e => setThumbProgress(Math.round((e.loaded / e.total) * 100));
xhr.open("PUT", presign.uploadUrl);
xhr.setRequestHeader("Content-Type", file.type);
xhr.send(file);

// 3. Lưu viewUrl vào course
await api.put(`/instructor/courses/${courseId}`, { thumbnailUrl: presign.viewUrl });
```

---

## 4. PreviewPlayer — Split view

### Layout tổng thể

```
[Topbar: back | logo | breadcrumb | badge "Xem trước"]
┌──────────────────────────────────────┬──────────────────┐
│  [Resource chips]          [Split ⊟] │                  │
├──────────────────┬───────────────────┤  Curriculum      │
│                  │                   │  (phải, 300px)   │
│   Viewer A       │   Viewer B        │  ┌────────────┐  │
│   (primary)      │   (split, opt)    │  │ Thumbnail  │  │
│                  │                   │  └────────────┘  │
│   Video / PDF /  │   PDF / DOC /     │  Tiêu đề KH      │
│   Slide / Ảnh    │   Slide / Ảnh     │  Progress bar    │
│                  │  [drag divider]   │  ── Chương 1     │
├──────────────────┴───────────────────┤    ▸ Bài 1       │
│  ← Bài trước          Bài tiếp →    │    ▸ Bài 2       │
└──────────────────────────────────────┴──────────────────┘
```

### Resource chips

Mỗi bài giảng có một "virtual video chip" (nếu có HLS) + các resource đính kèm:

```ts
const allChips = useMemo(() => {
  const list = [];
  if (active.hlsManifestUrl) {
    list.push({ id: "__video__", resourceType: "VIDEO_HLS",
      _label: "Video bài giảng", _hlsUrl: active.hlsManifestUrl });
  }
  (active.resources || []).forEach(r => list.push(r));
  return list;
}, [active]);
```

**Click chip** → load vào Viewer A.

**Khi Split active**, mỗi chip hiện thêm nút `B` khi hover → load vào Viewer B.

### Split view

```ts
const [splitActive, setSplitActive] = useState(false);
const [splitPct, setSplitPct] = useState(50); // % chiều rộng Viewer A
```

Drag divider để resize (giới hạn 20%–80%):

```ts
function handleDividerDrag(clientX) {
  const rect = contentRef.current.getBoundingClientRect();
  const pct = Math.min(80, Math.max(20, ((clientX - rect.left) / rect.width) * 100));
  setSplitPct(pct);
}
```

### Supported viewer types

| resourceType | Render |
|---|---|
| `VIDEO_HLS` | `<video src={_hlsUrl}>` |
| `VIDEO` (YouTube) | `<iframe youtube-nocookie>` |
| `VIDEO` (direct) | `<video src={url}>` |
| `PDF` | `<iframe src={url}>` trực tiếp |
| `DOC`, `SLIDE` | `<iframe src="docs.google.com/viewer?url=...">` |
| `IMAGE` | `<img>` centered |
| Other | Download link |

### Auto-load

Khi chuyển bài, Viewer A tự load chip đầu tiên (video hoặc tài liệu đầu), Viewer B reset về trống:

```ts
useEffect(() => {
  setViewerB(null); setSplitActive(false); setSplitPct(50);
  const first = chips[0] || null;
  setViewerA(first);
  if (first && first.resourceType !== "VIDEO_HLS") fetchResUrl(first, active.id);
}, [active?.id]);
```

---

## 5. Admin Approval — diff fix

### Vấn đề

Tab "Thay đổi" hiển thị các field (Tên khóa học, Cấp độ, Mô tả) kể cả khi giá trị TRƯỚC và SAU giống nhau.

### Nguyên nhân

`draftChanges` chỉ check `field` có tồn tại (truthy), không so sánh với giá trị live:

```ts
// Sai — draftTitle tồn tại là đủ để hiển thị
detail.draftTitle && { label: "Tên khóa học", old: detail.title, next: detail.draftTitle }
```

### Fix

```ts
// Đúng — chỉ hiện khi thực sự khác nhau
detail.draftTitle && detail.draftTitle !== detail.title
  && { label: "Tên khóa học", old: detail.title, next: detail.draftTitle },
detail.draftLevel && detail.draftLevel !== detail.level
  && { label: "Cấp độ", old: detail.level, next: detail.draftLevel },
detail.draftDescription && detail.draftDescription !== detail.description
  && { label: "Mô tả", old: detail.description, next: detail.draftDescription },
```

### So sánh: tab "So sánh" vs tab "Thay đổi"

| | So sánh (snapshot) | Thay đổi |
|---|---|---|
| Nguồn dữ liệu | API `/versions/diff` | Object `detail` sẵn có |
| Cơ chế | So sánh 2 version snapshot từ DB | So sánh field `draft*` vs field live |
| Độ chính xác | Cao (backend tính) | Phụ thuộc flag backend |
| API call thêm | Có | Không |

---

## 6. Rút gửi duyệt

### Vấn đề

Sau khi nộp version để duyệt (status `PENDING`), instructor không có cách rút lại để chỉnh sửa tiếp. Ngoài ra khi backend xử lý `withdrawFromReview`, record `CourseVersion` không được revert về `DRAFT`.

### Backend fix — CourseServiceImpl.java

```java
if (course.getStatus() == CourseStatus.PENDING) {
    course.setStatus(CourseStatus.DRAFT);
    course.setSubmittedAt(null);
    // Revert CourseVersion PENDING → DRAFT
    courseVersionRepository.findFirstByCourseIdAndStatus(courseId, "PENDING").ifPresent(v -> {
        v.setStatus("DRAFT");
        courseVersionRepository.save(v);
    });
} else if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
    initChapters(course);
    clearAllDrafts(course);
    course.setStatus(CourseStatus.PUBLISHED);
    course.setPendingUpdateAt(null);
    course.setSubmittedAt(null);
    // Revert CourseVersion PENDING → DRAFT
    courseVersionRepository.findFirstByCourseIdAndStatus(courseId, "PENDING").ifPresent(v -> {
        v.setStatus("DRAFT");
        courseVersionRepository.save(v);
    });
}
```

API endpoint: `PUT /instructor/courses/{courseId}/withdraw`

### Frontend — InsCourseDetail.tsx

```ts
async function handleWithdraw() {
  if (!confirm("Rút khỏi hàng chờ duyệt?\n\nPhiên bản đang chờ sẽ chuyển về bản nháp...")) return;
  try {
    await api.put(`/instructor/courses/${courseId}/withdraw`);
    await loadCourse(true);
    const vRes = await api.get(`/instructor/courses/${courseId}/versions`);
    setVersions(vRes.data || []);
  } catch (e) {
    alert(e?.response?.data?.message || "Rút gửi duyệt thất bại");
  }
}
```

### Frontend — CourseVersionsTab.tsx

Nút "Rút gửi duyệt" xuất hiện trên version có `status === "PENDING"`:

```tsx
{v.status === "PENDING" && handleWithdraw && (
  <button className="btn btn-ghost btn-sm"
    style={{ color: "#d97706", borderColor: "#fde68a" }}
    onClick={handleWithdraw}>
    <Ic n="undo" size={13} />
    Rút gửi duyệt
  </button>
)}
```

### Về lịch sử version

Các version `APPROVED` cũ **không bao giờ bị xóa** — `deleteDraftVersion` chỉ cho phép xóa `DRAFT` và `REJECTED`. Mọi phiên bản đã duyệt được giữ lại trong DB làm cơ sở rollback.

---

## Ghi chú kiến trúc

### Vòng đời version

```
[Instructor tạo nháp]
        ↓
     DRAFT  ←──────────────────┐
        ↓ nộp duyệt            │ rút gửi duyệt
     PENDING ──────────────────┘
        ↓ admin duyệt  ↓ admin từ chối
    APPROVED          REJECTED
        ↓                  ↓
   (live course)      có thể xóa
   (không xóa được)
```

### S3 presign flow

```
Frontend                 Backend              S3
   │── POST /presign ──→    │                  │
   │←── { uploadUrl,        │                  │
   │      viewUrl }         │                  │
   │                        │                  │
   │── PUT uploadUrl ───────────────────────→  │
   │   (XHR + progress)     │                  │
   │                        │                  │
   │── PUT /courses/{id} ──→│                  │
   │   { thumbnailUrl:      │                  │
   │     viewUrl }          │                  │
```
