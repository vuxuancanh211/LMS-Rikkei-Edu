-- Bỏ khái niệm "điểm/câu" (points) — mọi câu hỏi có trọng số bằng nhau, điểm bài làm
-- chỉ còn tính theo phần trăm số câu trả lời đúng / tổng số câu (xem QuizAttemptServiceImpl,
-- QuizServiceImpl#gradeDryRun). points_earned trên quiz_attempt_answers cũng bỏ vì thừa với
-- is_correct đã có sẵn (earned = isCorrect ? 1 : 0 khi mọi câu cùng trọng số).
ALTER TABLE "bank_questions" DROP COLUMN IF EXISTS "points";
ALTER TABLE "quiz_questions" DROP COLUMN IF EXISTS "points";
ALTER TABLE "quiz_attempt_answers" DROP COLUMN IF EXISTS "points_earned";
