ALTER TABLE lessons ADD COLUMN quiz_id UUID REFERENCES quizzes(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX idx_lessons_quiz_id ON lessons(quiz_id) WHERE quiz_id IS NOT NULL;
