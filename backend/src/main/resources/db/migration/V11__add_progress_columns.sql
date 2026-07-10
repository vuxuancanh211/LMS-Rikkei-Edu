ALTER TABLE lesson_progress
    ADD COLUMN IF NOT EXISTS document_view_seconds int DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lesson_percentage decimal(5,2);
