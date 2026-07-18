ALTER TABLE course_progress
    ADD COLUMN completed_assignments_count INTEGER DEFAULT 0,
    ADD COLUMN total_assignments_count    INTEGER DEFAULT 0;
