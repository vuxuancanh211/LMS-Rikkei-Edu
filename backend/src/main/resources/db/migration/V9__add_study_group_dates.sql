-- ============================================================
-- V8: Add date columns & updated_at to study_groups
-- ============================================================

ALTER TABLE study_groups
    ADD COLUMN IF NOT EXISTS start_date date NOT NULL DEFAULT CURRENT_DATE,
    ADD COLUMN IF NOT EXISTS end_date   date,
    ADD COLUMN IF NOT EXISTS updated_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_study_groups_start_date
    ON study_groups(start_date);
