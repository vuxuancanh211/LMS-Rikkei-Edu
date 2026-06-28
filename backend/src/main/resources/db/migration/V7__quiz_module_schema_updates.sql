-- V7: Quiz module schema updates to match final design
-- Replaces creation_mode/random_* columns with quiz_type/random_mode/difficulty_config
-- Adds cooldown, end_date, archived_at, difficulty clone on quiz_questions, time_spent on answers

-- =====================================================================
-- quizzes: restructure random config & add new fields
-- =====================================================================
ALTER TABLE quizzes
    DROP COLUMN IF EXISTS creation_mode,
    DROP COLUMN IF EXISTS random_subject_tag,
    DROP COLUMN IF EXISTS random_difficulty,
    DROP COLUMN IF EXISTS random_question_count,
    DROP COLUMN IF EXISTS show_answers_policy,
    DROP COLUMN IF EXISTS show_correct_answer,
    DROP COLUMN IF EXISTS total_points;

ALTER TABLE quizzes
    ADD COLUMN quiz_type          varchar(20),
    ADD COLUMN random_mode        varchar(20),
    ADD COLUMN difficulty_config  jsonb,
    ADD COLUMN subject_tag_filter varchar(100),
    ADD COLUMN cooldown_minutes   int DEFAULT 20,
    ADD COLUMN end_date           timestamptz,
    ADD COLUMN archived_at        timestamptz;

COMMENT ON COLUMN quizzes.quiz_type         IS 'STATIC | SHUFFLED_POOL | RANDOM_DRAW';
COMMENT ON COLUMN quizzes.random_mode       IS 'FULLY_RANDOM | BY_DIFFICULTY — chỉ dùng khi quiz_type = RANDOM_DRAW';
COMMENT ON COLUMN quizzes.difficulty_config IS '{"easy":3,"medium":3,"hard":4} — chỉ dùng khi random_mode = BY_DIFFICULTY';
COMMENT ON COLUMN quizzes.status            IS 'DRAFT | PUBLISHED | ARCHIVED';

-- =====================================================================
-- quiz_questions: thêm difficulty + subject_tag (clone từ bank lúc snapshot)
-- =====================================================================
ALTER TABLE quiz_questions
    ADD COLUMN IF NOT EXISTS difficulty   varchar(10),
    ADD COLUMN IF NOT EXISTS subject_tag  varchar(100);

COMMENT ON COLUMN quiz_questions.difficulty  IS 'Clone từ bank_question lúc snapshot: EASY | MEDIUM | HARD';
COMMENT ON COLUMN quiz_questions.subject_tag IS 'Clone từ bank_question lúc snapshot';

-- =====================================================================
-- quiz_attempt_answers: thêm time_spent_seconds
-- =====================================================================
ALTER TABLE quiz_attempt_answers
    ADD COLUMN IF NOT EXISTS time_spent_seconds int;

-- =====================================================================
-- Indexes
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_bank_questions_course_status  ON bank_questions(course_id, status);
CREATE INDEX IF NOT EXISTS idx_bank_questions_difficulty      ON bank_questions(difficulty);
CREATE INDEX IF NOT EXISTS idx_bank_questions_tag             ON bank_questions(subject_tag) WHERE subject_tag IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_quiz_questions_bank_ref        ON quiz_questions(bank_question_id) WHERE bank_question_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_attempt_answers_question       ON quiz_attempt_answers(question_id);
CREATE INDEX IF NOT EXISTS idx_attempt_answers_attempt        ON quiz_attempt_answers(attempt_id);
CREATE INDEX IF NOT EXISTS idx_quizzes_course_status          ON quizzes(course_id, status);
