-- ============================================================
-- V22: Soft-delete cho chapters/lessons — tránh mất tài liệu khi rollback
-- ============================================================
-- Trước đây khi admin duyệt xóa 1 chương/bài (pendingDelete), Hibernate orphanRemoval xóa cứng
-- hẳn row chapters/lessons, kéo theo xóa cứng (ON DELETE CASCADE) toàn bộ lesson_resources của
-- chương/bài đó. CourseVersion.snapshot lưu tài liệu bằng s3Key (không lưu ID), nên khi rollback
-- về 1 phiên bản cũ còn cần tài liệu đó, không có cách nào phục hồi vì row + file đã mất hẳn.
-- Thêm deleted_at để chuyển sang soft-delete (giữ nguyên row), giống cách lesson_resources đã làm.

ALTER TABLE chapters ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
ALTER TABLE lessons  ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_chapters_deleted_at ON chapters(course_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_lessons_deleted_at ON lessons(course_id) WHERE deleted_at IS NULL;
