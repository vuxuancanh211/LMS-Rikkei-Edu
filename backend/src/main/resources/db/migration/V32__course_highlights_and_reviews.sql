-- "Bạn sẽ học được gì" và "Yêu cầu đầu vào" — lưu dạng JSON array of string ngay trên bảng
-- courses, theo cùng cách các field thông tin cơ bản khác (title/description/level) được cập
-- nhật trực tiếp không qua draft/duyệt (xem CourseServiceImpl#updateCourse).
ALTER TABLE courses ADD COLUMN IF NOT EXISTS learning_outcomes text;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS requirements text;

