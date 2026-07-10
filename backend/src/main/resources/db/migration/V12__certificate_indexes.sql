CREATE INDEX IF NOT EXISTS idx_certificates_student_issued_at
    ON certificates(student_id, issued_at DESC);

CREATE INDEX IF NOT EXISTS idx_certificates_status_issued_at
    ON certificates(status, issued_at DESC);

CREATE INDEX IF NOT EXISTS idx_certificates_course_id
    ON certificates(course_id);
