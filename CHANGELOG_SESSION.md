# Changelog — Session 2026-06-24

## 1. Fix UI: Ẩn nút live-editing khi đang xem snapshot

**File:** `frontend/src/pages/Instructor/InsCourseDetail.tsx`

Thêm điều kiện `!viewingVersion` vào tất cả nút và banner thuộc chế độ live-editing:
- "Gửi duyệt", "Lưu bản nháp", "Gửi cập nhật", "Hủy thay đổi", "Hủy cập nhật", "Rút duyệt"
- Banner PENDING_UPDATE, PUBLISHED (có/không có thay đổi), rejection reason

Kết quả: khi user đang xem một phiên bản snapshot (DRAFT/APPROVED/REJECTED), các controls của live-editing không hiện nữa.

---

## 2. Xóa phiên bản (hard delete) + S3 cleanup

### Backend

**`CourseSnapshotDto.ResourceSnap`** — thêm field `s3Key` (null nếu external URL).

**`buildSnapshot`** — populate `s3Key` vào snapshot khi lưu.

**`LessonResourceRepository`** — thêm `existsByS3KeyAndDeletedAtIsNull(String s3Key)`.

**`CourseServiceImpl`**:
- `deleteDraftVersion`: mở rộng cho phép xóa cả REJECTED (không chỉ DRAFT).
- Thêm `cleanupSnapshotS3Keys(snapshotJson)`: parse snapshot → xóa S3 keys không còn được tham chiếu trong `lesson_resources`.
- `clearAllDrafts`: thu thập S3 keys trước khi cascade delete → xóa S3 sau khi DB xóa xong.
- Inject `S3Service` vào `CourseServiceImpl`.

**`CourseController`** — endpoint `PATCH /{courseId}/versions/{versionId}/label` (đổi tên bản nháp).

**`CourseService` interface** — thêm `renameDraftVersion`.

### Frontend

- Tab lịch sử: nút **"Xóa phiên bản từ chối"** cho version REJECTED.
- Snapshot view banner: nút **"Xóa phiên bản"** (đỏ) cho DRAFT/REJECTED → confirm → xóa → về bản đang sửa.
- Tách `deleteVersion(versionId)` (gọi API) và `handleDeleteDraft(versionId)` (confirm + gọi).

---

## 3. Đổi tên bản nháp (inline rename)

### Backend

**`CourseService`** — thêm `renameDraftVersion(instructorId, courseId, versionId, label)`.

**`CourseServiceImpl`** — implement: chỉ cho phép đổi tên DRAFT, trim label.

**`CourseController`** — `PATCH /{courseId}/versions/{versionId}/label`.

### Frontend

- Thêm state `renamingVersion`, `renameInput`.
- Thêm `handleRenameVersion(versionId, newLabel)`.
- Mỗi bản nháp DRAFT trong tab Phiên bản hiển thị nút **"đổi tên"** → click → input inline → Enter/Lưu xác nhận, Esc/Hủy thoát.
- Cập nhật cả `viewingVersion` nếu đang xem snapshot đó.

---

## 4. Fix 2 PENDING versions + UI không refresh sau submit

### Backend

**`CourseServiceImpl`**:
- Tách helper `revertPendingVersionsToDraft(courseId)` dùng chung.
- `submitForApproval` (PENDING_UPDATE, PUBLISHED) gọi `revertPendingVersionsToDraft` trước khi tạo version mới → không bao giờ có 2 PENDING cùng lúc.
- `submitVersion` cũng dùng helper này.

### Frontend

- `handleSubmitVersion` sau khi thành công: gọi `Promise.all([reload versions, reload course])` thay vì chỉ patch local state.
- Kết quả: dropdown cập nhật màu sắc/label ngay, nút "Gửi cập nhật" tự ẩn.

**SQL cleanup** (chạy thủ công nếu DB đang có 2 PENDING):
```sql
UPDATE course_versions SET status = 'DRAFT'
WHERE status = 'PENDING'
  AND id NOT IN (
    SELECT DISTINCT ON (course_id) id FROM course_versions
    WHERE status = 'PENDING' ORDER BY course_id, submitted_at DESC
  );
```

---

## 5. Tách tab "Phiên bản" và "Lịch sử duyệt"

**File:** `frontend/src/pages/Instructor/InsCourseDetail.tsx`

Trước: 1 tab "Lịch sử duyệt" chứa cả version cards lẫn approval log (log không được render).

Sau: 2 tab riêng:
- **Phiên bản** (`v: "versions"`): DRAFT cards (có đổi tên, xóa, nộp duyệt, rollback) + PENDING/APPROVED/REJECTED cards.
- **Lịch sử duyệt** (`v: "history"`): timeline sự kiện (SUBMITTED_FIRST, SUBMITTED_UPDATE, APPROVED, REJECTED, WITHDRAWN) với timestamp và lý do từ chối.

---

## 6. Xem tài liệu trong bản đang chỉnh sửa và snapshot

### Backend

**`S3Service`** — thêm `generatePresignedInlineUrl(key, expirySeconds)`: presigned GET URL với `responseContentDisposition: "inline"` → browser hiển thị thay vì tải.

**`LessonResourceService` / `LessonResourceServiceImpl`** — thêm `getViewUrl(...)`: trả URL inline.

**`CourseController`**:
- `GET /{courseId}/lessons/{lessonId}/resources/{resourceId}/view-url` — presigned inline URL cho live resource.
- `GET /{courseId}/resources/presign-view?s3Key=...` — presigned inline URL từ s3Key (dùng cho snapshot).
- `GET /{courseId}/resources/presign-download?s3Key=...` — presigned download URL từ s3Key (dùng cho snapshot).

**`CourseRepository`** — thêm `existsByIdAndInstructorId(UUID id, UUID instructorId)` để ownership check nhẹ.

**`LessonResourceServiceImpl`**:
- Thêm `verifyOwnership(instructorId, courseId)`: chỉ check ownership, không block theo trạng thái course.
- `getDownloadUrl` dùng `verifyOwnership` thay vì `loadOwnedLesson` → không còn bị block khi course PENDING/PENDING_UPDATE.

### Frontend

Mỗi tài liệu trong **bản đang chỉnh sửa** và **snapshot view** có 2 nút:
- **Xem** (xanh dương): gọi `view-url` / `presign-view` → `window.open(..., "_blank")` → browser hiển thị inline.
- **Tải** (xanh lá): gọi `download-url` / `presign-download` → tạo `<a download>` → browser tải file.

Snapshot cũ (lưu trước khi thêm `s3Key`): hiện thông báo hướng dẫn lưu lại bản nháp mới.

---

## Tổng hợp endpoints mới

| Method | Path | Mô tả |
|--------|------|--------|
| `PATCH` | `/instructor/courses/{courseId}/versions/{versionId}/label` | Đổi tên bản nháp DRAFT |
| `GET` | `/instructor/courses/{courseId}/lessons/{lessonId}/resources/{resourceId}/view-url` | Presigned inline URL để xem |
| `GET` | `/instructor/courses/{courseId}/resources/presign-view?s3Key=` | Inline URL từ s3Key (snapshot) |
| `GET` | `/instructor/courses/{courseId}/resources/presign-download?s3Key=` | Download URL từ s3Key (snapshot) |
