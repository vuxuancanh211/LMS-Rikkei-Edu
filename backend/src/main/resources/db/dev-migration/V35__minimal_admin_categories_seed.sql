-- ============================================================
-- RIKKEI EDU LMS - MINIMAL DEV FOUNDATION SEED
-- Xóa toàn bộ dữ liệu nghiệp vụ và chỉ nạp dữ liệu nền tối thiểu:
-- 1 tài khoản quản trị viên và 10 danh mục khóa học.
-- Mật khẩu mặc định của tài khoản seed: 123456
-- ============================================================

TRUNCATE TABLE
    bank_question_embeddings,
    ai_question_generation_jobs,
    quiz_attempt_answers,
    proctoring_violation_logs,
    quiz_attempts,
    quiz_options,
    quiz_questions,
    bank_options,
    bank_questions,
    quizzes,
    proctoring_configs,
    certificates,
    notification_preferences,
    notifications,
    outbox_events,
    ai_message_debugs,
    ai_messages,
    ai_conversations,
    chat_message_reactions,
    chat_messages,
    chat_room_members,
    chat_rooms,
    forum_reports,
    forum_replies,
    forum_reactions,
    forum_attachments,
    forum_posts,
    submission_files,
    assignment_submissions,
    assignment_attachments,
    assignment_groups,
    assignments,
    group_members,
    study_groups,
    course_progress,
    lesson_progress,
    ai_chunk_references,
    document_chunks,
    ai_ingestion_jobs,
    ai_sources,
    video_upload_jobs,
    lesson_resources,
    lessons,
    chapters,
    course_versions,
    course_approval_logs,
    course_enrollments,
    courses,
    course_categories,
    bulk_import_jobs,
    audit_logs,
    user_preferences,
    users
RESTART IDENTITY CASCADE;

-- ============================================================
-- ADMIN USER
-- Email: admin@rikkei.edu
-- Password: 123456
-- ============================================================
INSERT INTO users (
    id,
    email,
    full_name,
    password_hash,
    role,
    status,
    phone_number,
    bio,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@rikkei.edu',
    'Quản trị viên Hệ thống',
    '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
    'ADMIN',
    'ACTIVE',
    '0900000001',
    'Tài khoản quản trị viên dùng để thiết lập, vận hành và kiểm thử hệ thống LMS Rikkei Edu.',
    now(),
    now()
);

-- ============================================================
-- COURSE CATEGORIES
-- ============================================================
INSERT INTO course_categories (id, name, slug, is_active, created_at) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Backend Development', 'backend-development', true, now()),
    ('10000000-0000-0000-0000-000000000002', 'Frontend Development', 'frontend-development', true, now()),
    ('10000000-0000-0000-0000-000000000003', 'Database Systems', 'database-systems', true, now()),
    ('10000000-0000-0000-0000-000000000004', 'DevOps & Cloud Infrastructure', 'devops-cloud-infrastructure', true, now()),
    ('10000000-0000-0000-0000-000000000005', 'Mobile Application Development', 'mobile-application-development', true, now()),
    ('10000000-0000-0000-0000-000000000006', 'AI & Machine Learning', 'ai-machine-learning', true, now()),
    ('10000000-0000-0000-0000-000000000007', 'Data Engineering', 'data-engineering', true, now()),
    ('10000000-0000-0000-0000-000000000008', 'Software Testing & Quality Assurance', 'software-testing-quality-assurance', true, now()),
    ('10000000-0000-0000-0000-000000000009', 'System Design & Software Architecture', 'system-design-software-architecture', true, now()),
    ('10000000-0000-0000-0000-000000000010', 'Cybersecurity & Secure Coding', 'cybersecurity-secure-coding', true, now());

-- ============================================================
-- SUMMARY
-- Users: 1 admin
-- Course categories: 10
-- Courses, lessons, groups, assignments, quizzes, notifications: 0
-- ============================================================
