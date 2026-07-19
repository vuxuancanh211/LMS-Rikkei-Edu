-- Thêm cột random_total_count để lưu số câu cần rút khi random_mode = FULLY_RANDOM
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS random_total_count INTEGER;
