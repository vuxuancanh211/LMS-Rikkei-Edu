-- "Bạn sẽ học được gì" và "Yêu cầu đầu vào" — lưu dạng JSON array of string ngay trên bảng
-- courses, theo cùng cách các field thông tin cơ bản khác (title/description/level) được cập
-- nhật trực tiếp không qua draft/duyệt (xem CourseServiceImpl#updateCourse).
ALTER TABLE courses ADD COLUMN learning_outcomes text;
ALTER TABLE courses ADD COLUMN requirements text;

-- Đánh giá sao của học viên đã học khóa — mỗi học viên chỉ đánh giá 1 lần/khóa (có thể sửa lại).
CREATE TABLE course_reviews (
    id          uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
    course_id   uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    student_id  uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      smallint NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_course_review_student UNIQUE (course_id, student_id)
);
CREATE INDEX idx_course_reviews_course_id ON course_reviews(course_id);
