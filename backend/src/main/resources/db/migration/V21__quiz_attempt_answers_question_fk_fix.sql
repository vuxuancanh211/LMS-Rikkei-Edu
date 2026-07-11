-- quiz_attempt_answers.question_id chứa question_id thật (quiz_questions.id) cho quiz STATIC/SHUFFLED_POOL,
-- nhưng chứa bank_question_id (bank_questions.id) cho quiz RANDOM_DRAW — vì RANDOM_DRAW rút câu hỏi trực
-- tiếp từ ngân hàng lúc làm bài, không bao giờ clone sang bảng quiz_questions (xem
-- QuizAttemptServiceImpl#snapshotToTransient). FK ràng buộc chỉ về quiz_questions khiến MỌI lần nộp bài
-- RANDOM_DRAW đều vỡ constraint và rollback toàn bộ transaction nộp bài (kể cả auto-submit hết giờ).
ALTER TABLE quiz_attempt_answers DROP CONSTRAINT IF EXISTS quiz_attempt_answers_question_id_fkey;
ALTER TABLE quiz_attempt_answers DROP CONSTRAINT IF EXISTS quiz_attempt_answers_question_id_fkey1;
ALTER TABLE quiz_attempt_answers DROP CONSTRAINT IF EXISTS quiz_attempt_answers_question_id_fkey2;
ALTER TABLE quiz_attempt_answers DROP CONSTRAINT IF EXISTS quiz_attempt_answers_question_id_fkey3;
