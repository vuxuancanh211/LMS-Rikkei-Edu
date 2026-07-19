-- Create the btree_gin extension for composite GIN indexes over scalar types
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- Create a composite B-Tree index for purely filter-based queries
CREATE INDEX IF NOT EXISTS idx_bank_questions_filters 
ON bank_questions (course_id, status, difficulty, subject_tag);

-- Drop the old FTS-only index to replace with a super index
DROP INDEX IF EXISTS idx_bank_questions_fts;

-- Create a Super GIN index combining scalar filters + Full-Text Search
CREATE INDEX IF NOT EXISTS idx_bank_questions_super_gin 
ON bank_questions 
USING GIN (course_id, status, difficulty, to_tsvector('simple', immutable_unaccent(coalesce(question_text, ''))));
