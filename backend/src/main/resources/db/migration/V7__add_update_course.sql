-- ============================================================
-- V7: Hybrid versioning — draft fields + course_versions
-- ============================================================

-- Draft metadata fields trên courses
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS draft_title            varchar(200),
    ADD COLUMN IF NOT EXISTS draft_description      text,
    ADD COLUMN IF NOT EXISTS draft_thumbnail_url    text,
    ADD COLUMN IF NOT EXISTS draft_level            varchar(20),
    ADD COLUMN IF NOT EXISTS change_summary         varchar(500),
    ADD COLUMN IF NOT EXISTS draft_rejection_reason text;

-- Draft/pending flags trên chapters
ALTER TABLE chapters
    ADD COLUMN IF NOT EXISTS is_draft       boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS pending_delete boolean NOT NULL DEFAULT false;

-- Draft/pending flags + draft content trên lessons
ALTER TABLE lessons
    ADD COLUMN IF NOT EXISTS is_draft            boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS pending_delete      boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS draft_title         varchar(200),
    ADD COLUMN IF NOT EXISTS draft_content_text  text;

-- lesson_resources: flags theo dõi thay đổi trong update flow
ALTER TABLE lesson_resources
    ADD COLUMN IF NOT EXISTS is_new_in_update boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS pending_delete   boolean NOT NULL DEFAULT false;

-- Thêm snapshot vào approval log (nếu chưa có từ V1)
ALTER TABLE course_approval_logs
    ADD COLUMN IF NOT EXISTS snapshot text;

-- Indexes hỗ trợ query draft content
CREATE INDEX IF NOT EXISTS idx_chapters_is_draft
    ON chapters(course_id) WHERE is_draft = true;

CREATE INDEX IF NOT EXISTS idx_lessons_is_draft
    ON lessons(course_id) WHERE is_draft = true;

CREATE INDEX IF NOT EXISTS idx_lessons_pending_delete
    ON lessons(course_id) WHERE pending_delete = true;

-- Bảng lưu snapshot phiên bản khóa học
CREATE TABLE IF NOT EXISTS course_versions (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id        uuid        NOT NULL,
    version_number   int,
    label            varchar(100),
    status           varchar(20) NOT NULL DEFAULT 'DRAFT',
    snapshot         text,
    change_summary   text,
    rejection_reason text,
    submitted_by     uuid,
    reviewed_by      uuid,
    submitted_at     timestamptz,
    reviewed_at      timestamptz,
    CONSTRAINT uq_course_version UNIQUE (course_id, version_number)
);

ALTER TABLE course_versions
    ADD CONSTRAINT fk_course_versions_course
        FOREIGN KEY (course_id) REFERENCES courses (id) DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX IF NOT EXISTS idx_course_versions_course_status
    ON course_versions(course_id, status);
