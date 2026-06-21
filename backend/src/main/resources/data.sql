-- ============================================================
-- RIKKEI EDU LMS — SEED DATA (Dev/Testing Local)
-- 10 users · 5 courses · full flow
-- Password for all users: Test@123456
-- BCrypt hash of "Test@123456"
-- ============================================================

BEGIN;

-- ============================================================
-- 1. USERS
-- 1 admin, 2 instructors, 7 students
-- ============================================================
INSERT INTO users (id, email, full_name, password_hash, role, status, phone_number, bio, created_at, updated_at) VALUES
                                                                                                                     ('00000000-0000-0000-0000-000000000001', 'admin@rikkei.edu', 'Admin System',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'ADMIN', 'ACTIVE', '0900000001', 'Quản trị viên hệ thống', now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000002', 'instructor1@rikkei.edu', 'Nguyễn Văn Minh',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'INSTRUCTOR', 'ACTIVE', '0900000002', 'Giảng viên Java Backend 8 năm kinh nghiệm', now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000003', 'instructor2@rikkei.edu', 'Trần Thị Lan',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'INSTRUCTOR', 'ACTIVE', '0900000003', 'Giảng viên Frontend ReactJS & UX Design', now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000004', 'student1@rikkei.edu', 'Phạm Quốc Hùng',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'STUDENT', 'ACTIVE', '0900000004', NULL, now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000005', 'student2@rikkei.edu', 'Lê Thị Mai',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'STUDENT', 'ACTIVE', '0900000005', NULL, now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000006', 'student3@rikkei.edu', 'Hoàng Minh Tuấn',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'STUDENT', 'ACTIVE', '0900000006', NULL, now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000007', 'student4@rikkei.edu', 'Đặng Thị Hoa',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'STUDENT', 'ACTIVE', '0900000007', NULL, now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000008', 'student5@rikkei.edu', 'Vũ Đức Long',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'STUDENT', 'ACTIVE', '0900000008', NULL, now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000009', 'student6@rikkei.edu', 'Bùi Thị Thu',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'STUDENT', 'ACTIVE', '0900000009', NULL, now(), now()),

                                                                                                                     ('00000000-0000-0000-0000-000000000010', 'vuxuancanh2004@gmail.com', 'Vũ Xuân Cảnh',
                                                                                                                       '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
                                                                                                                      'STUDENT', 'ACTIVE', '0900000010', NULL, now(), now());

-- ============================================================
-- 2. COURSE CATEGORIES
-- ============================================================
INSERT INTO course_categories (id, name, slug, is_active, created_at) VALUES
                                                                          ('10000000-0000-0000-0000-000000000001', 'Backend Development', 'backend-development', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000002', 'Frontend Development', 'frontend-development', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000003', 'DevOps & Cloud',       'devops-cloud',         true, now()),
                                                                          ('10000000-0000-0000-0000-000000000004', 'Database',             'database',             true, now()),
                                                                          ('10000000-0000-0000-0000-000000000005', 'AI & Machine Learning','ai-machine-learning',  true, now());

-- ============================================================
-- 3. COURSES (5 courses, mixed status)
-- ============================================================
INSERT INTO courses (id, instructor_id, category_id, title, slug, description, level, status, chat_enabled, published_at, created_at, updated_at) VALUES
                                                                                                                                                      ('20000000-0000-0000-0000-000000000001',
                                                                                                                                                       '00000000-0000-0000-0000-000000000002',
                                                                                                                                                       '10000000-0000-0000-0000-000000000001',
                                                                                                                                                       'Java Spring Boot từ Zero đến Hero',
                                                                                                                                                       'java-spring-boot-zero-to-hero',
                                                                                                                                                       'Khoá học Java Spring Boot toàn diện: REST API, Spring Security, JPA, Docker deployment.',
                                                                                                                                                       'INTERMEDIATE', 'PUBLISHED', true, now() - interval '30 days', now() - interval '35 days', now()),

                                                                                                                                                      ('20000000-0000-0000-0000-000000000002',
                                                                                                                                                       '00000000-0000-0000-0000-000000000002',
                                                                                                                                                       '10000000-0000-0000-0000-000000000004',
                                                                                                                                                       'PostgreSQL Nâng Cao & Query Optimization',
                                                                                                                                                       'postgresql-nang-cao-query-optimization',
                                                                                                                                                       'Index, Execution Plan, Partitioning, Replication và tối ưu hoá hiệu năng.',
                                                                                                                                                       'ADVANCED', 'PUBLISHED', true, now() - interval '20 days', now() - interval '25 days', now()),

                                                                                                                                                      ('20000000-0000-0000-0000-000000000003',
                                                                                                                                                       '00000000-0000-0000-0000-000000000003',
                                                                                                                                                       '10000000-0000-0000-0000-000000000002',
                                                                                                                                                       'ReactJS & TailwindCSS Thực Chiến',
                                                                                                                                                       'reactjs-tailwindcss-thuc-chien',
                                                                                                                                                       'Xây dựng SPA hoàn chỉnh với ReactJS, Hooks, Context API và TailwindCSS.',
                                                                                                                                                       'BEGINNER', 'PUBLISHED', false, now() - interval '15 days', now() - interval '18 days', now()),

                                                                                                                                                      ('20000000-0000-0000-0000-000000000004',
                                                                                                                                                       '00000000-0000-0000-0000-000000000003',
                                                                                                                                                       '10000000-0000-0000-0000-000000000003',
                                                                                                                                                       'Docker & Kubernetes Cơ Bản',
                                                                                                                                                       'docker-kubernetes-co-ban',
                                                                                                                                                       'Container hoá ứng dụng, viết Dockerfile, Docker Compose và deploy lên K8s.',
                                                                                                                                                       'INTERMEDIATE', 'PUBLISHED', false, now() - interval '10 days', now() - interval '12 days', now()),

                                                                                                                                                      ('20000000-0000-0000-0000-000000000005',
                                                                                                                                                       '00000000-0000-0000-0000-000000000002',
                                                                                                                                                       '10000000-0000-0000-0000-000000000005',
                                                                                                                                                       'RAG với LangChain và OpenAI',
                                                                                                                                                       'rag-langchain-openai',
                                                                                                                                                       'Xây dựng hệ thống hỏi đáp tài liệu với Retrieval-Augmented Generation.',
                                                                                                                                                       'ADVANCED', 'DRAFT', false, NULL, now() - interval '3 days', now());

-- ============================================================
-- 4. COURSE APPROVAL LOGS (cho 4 courses đã PUBLISHED)
-- ============================================================
INSERT INTO course_approval_logs (id, course_id, admin_id, action, reason, created_at) VALUES
                                                                                           (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'APPROVED', 'Nội dung chất lượng, phù hợp chương trình.', now() - interval '30 days'),
                                                                                           (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'APPROVED', 'Nội dung chuyên sâu, đúng yêu cầu.', now() - interval '20 days'),
                                                                                           (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'APPROVED', NULL, now() - interval '15 days'),
                                                                                           (gen_random_uuid(), '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'APPROVED', NULL, now() - interval '10 days');

-- ============================================================
-- 5. CHAPTERS (mỗi course 2-3 chapters)
-- ============================================================
INSERT INTO chapters (id, course_id, title, order_index, created_at) VALUES
                                                                         -- Course 1: Spring Boot
                                                                         ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Giới thiệu & Cài đặt môi trường', 1, now()),
                                                                         ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'Xây dựng REST API',               2, now()),
                                                                         ('30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', 'Spring Security & JWT',           3, now()),
                                                                         -- Course 2: PostgreSQL
                                                                         ('30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002', 'Index & Execution Plan',          1, now()),
                                                                         ('30000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000002', 'Partitioning & Replication',      2, now()),
                                                                         -- Course 3: ReactJS
                                                                         ('30000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000003', 'React Fundamentals',              1, now()),
                                                                         ('30000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000003', 'Hooks & State Management',        2, now()),
                                                                         -- Course 4: Docker
                                                                         ('30000000-0000-0000-0000-000000000008', '20000000-0000-0000-0000-000000000004', 'Docker Fundamentals',             1, now()),
                                                                         ('30000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000004', 'Kubernetes Basics',               2, now()),
                                                                         -- Course 5: RAG
                                                                         ('30000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000005', 'LangChain & Vector DB',           1, now());

-- ============================================================
-- 6. LESSONS (mỗi chapter 2-3 lessons)
-- ============================================================
INSERT INTO lessons (id, chapter_id, course_id, title, order_index, type, content_text, duration_seconds, is_preview, video_status, created_at, updated_at) VALUES
                                                                                                                                                                -- Chapter 1 (Spring Boot - Intro)
                                                                                                                                                                ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                 'Spring Boot là gì?', 1, 'VIDEO', NULL, 720, true, 'READY', now(), now()),
                                                                                                                                                                ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                 'Cài đặt JDK, IntelliJ & Maven', 2, 'VIDEO', NULL, 900, false, 'READY', now(), now()),
                                                                                                                                                                -- Chapter 2 (Spring Boot - REST)
                                                                                                                                                                ('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                 'Tạo Controller & Endpoint đầu tiên', 1, 'VIDEO', NULL, 1200, false, 'READY', now(), now()),
                                                                                                                                                                ('40000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                 'Request Validation & Exception Handling', 2, 'TEXT',
                                                                                                                                                                 '# Request Validation\nSử dụng @Valid và @ExceptionHandler để xử lý lỗi đầu vào.', NULL, false, 'NONE', now(), now()),
                                                                                                                                                                -- Chapter 3 (Spring Boot - Security)
                                                                                                                                                                ('40000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                 'Spring Security Overview', 1, 'VIDEO', NULL, 1080, false, 'READY', now(), now()),
                                                                                                                                                                ('40000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                 'Tích hợp JWT Authentication', 2, 'VIDEO', NULL, 1440, false, 'READY', now(), now()),
                                                                                                                                                                -- Chapter 4 (PostgreSQL - Index)
                                                                                                                                                                ('40000000-0000-0000-0000-000000000007', '30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002',
                                                                                                                                                                 'B-Tree Index hoạt động thế nào?', 1, 'VIDEO', NULL, 960, true, 'READY', now(), now()),
                                                                                                                                                                ('40000000-0000-0000-0000-000000000008', '30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002',
                                                                                                                                                                 'EXPLAIN ANALYZE & Execution Plan', 2, 'VIDEO', NULL, 1320, false, 'READY', now(), now()),
                                                                                                                                                                -- Chapter 6 (React - Fundamentals)
                                                                                                                                                                ('40000000-0000-0000-0000-000000000009', '30000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000003',
                                                                                                                                                                 'JSX & Component cơ bản', 1, 'VIDEO', NULL, 840, true, 'READY', now(), now()),
                                                                                                                                                                ('40000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000003',
                                                                                                                                                                 'Props & State', 2, 'VIDEO', NULL, 900, false, 'READY', now(), now());

-- ============================================================
-- 7. COURSE ENROLLMENTS
-- ============================================================
INSERT INTO course_enrollments (id, course_id, student_id, enrolled_by, enrolled_at) VALUES
                                                                                         -- Course 1: 5 students
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', NULL, now() - interval '25 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005', NULL, now() - interval '24 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000006', NULL, now() - interval '22 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000007', NULL, now() - interval '20 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000001', now() - interval '18 days'),
                                                                                         -- Course 2: 3 students
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004', NULL, now() - interval '18 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000006', NULL, now() - interval '15 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000009', NULL, now() - interval '12 days'),
                                                                                         -- Course 3: 4 students
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000005', NULL, now() - interval '14 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000007', NULL, now() - interval '13 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000008', NULL, now() - interval '12 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', NULL, now() - interval '10 days'),
                                                                                         -- Course 4: 2 students
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000009', NULL, now() - interval '8 days'),
                                                                                         (gen_random_uuid(), '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', NULL, now() - interval '7 days');

-- ============================================================
-- 8. LESSON PROGRESS (student1 đã học xong course 1)
-- ============================================================
INSERT INTO lesson_progress (id, student_id, lesson_id, course_id, status, watched_percentage, last_playback_position, first_accessed_at, last_accessed_at, completed_at) VALUES
                                                                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'COMPLETED', 100.00, 720,  now() - interval '24 days', now() - interval '24 days', now() - interval '24 days'),
                                                                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'COMPLETED', 100.00, 900,  now() - interval '23 days', now() - interval '23 days', now() - interval '23 days'),
                                                                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', 'COMPLETED', 100.00, 1200, now() - interval '22 days', now() - interval '22 days', now() - interval '22 days'),
                                                                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001', 'COMPLETED', 100.00, NULL, now() - interval '21 days', now() - interval '21 days', now() - interval '21 days'),
                                                                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001', 'COMPLETED', 100.00, 1080, now() - interval '20 days', now() - interval '20 days', now() - interval '20 days'),
                                                                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001', 'COMPLETED', 100.00, 1440, now() - interval '19 days', now() - interval '19 days', now() - interval '19 days'),
                                                                                                                                                                              -- student2 đang học dở course 1
                                                                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000005', '40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'COMPLETED', 100.00, 720,  now() - interval '22 days', now() - interval '22 days', now() - interval '22 days'),
                                                                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000005', '40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'IN_PROGRESS', 60.00, 540, now() - interval '21 days', now() - interval '1 day',  NULL);

-- ============================================================
-- 9. COURSE PROGRESS
-- ============================================================
INSERT INTO course_progress (id, student_id, course_id, completed_lessons_count, total_lessons_count, overall_percentage, status, updated_at) VALUES
                                                                                                                                                  (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001', 6, 6, 100.00, 'COMPLETED', now() - interval '19 days'),
                                                                                                                                                  (gen_random_uuid(), '00000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001', 1, 6, 16.67,  'IN_PROGRESS', now() - interval '1 day'),
                                                                                                                                                  (gen_random_uuid(), '00000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001', 0, 6, 0.00,   'IN_PROGRESS', now() - interval '22 days'),
                                                                                                                                                  (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002', 0, 2, 0.00,   'IN_PROGRESS', now() - interval '18 days');

-- ============================================================
-- 10. STUDY GROUPS
-- ============================================================
INSERT INTO study_groups (id, course_id, instructor_id, name, description, max_capacity, created_at) VALUES
                                                                                                         ('50000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
                                                                                                          'Nhóm Spring Boot - Buổi Tối', 'Học buổi tối 20h-22h, thứ 2-4-6', 15, now() - interval '20 days'),
                                                                                                         ('50000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
                                                                                                          'Nhóm Spring Boot - Cuối Tuần',  'Học cuối tuần 9h-12h', 10, now() - interval '18 days'),
                                                                                                         ('50000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003',
                                                                                                          'Nhóm ReactJS - Beginner', 'Dành cho người mới bắt đầu', 12, now() - interval '12 days');

INSERT INTO group_members (id, group_id, student_id, joined_at) VALUES
                                                                    (gen_random_uuid(), '50000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', now() - interval '19 days'),
                                                                    (gen_random_uuid(), '50000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005', now() - interval '18 days'),
                                                                    (gen_random_uuid(), '50000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000006', now() - interval '17 days'),
                                                                    (gen_random_uuid(), '50000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000007', now() - interval '15 days'),
                                                                    (gen_random_uuid(), '50000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000008', now() - interval '14 days'),
                                                                    (gen_random_uuid(), '50000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000005', now() - interval '10 days'),
                                                                    (gen_random_uuid(), '50000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000010', now() - interval '9 days');

-- ============================================================
-- 11. QUIZZES & QUESTIONS
-- ============================================================
INSERT INTO quizzes (id, course_id, created_by, title, description, creation_mode, duration_minutes, max_attempts, pass_score, total_points, show_answers_policy, shuffle_questions, shuffle_options, proctoring_enabled, status, published_at, created_at, updated_at) VALUES
                                                                                                                                                                                                                                                                            ('60000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                                                                                             '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                                                                                             '00000000-0000-0000-0000-000000000002',
                                                                                                                                                                                                                                                                             'Quiz: REST API & Spring MVC', 'Kiểm tra kiến thức REST API với Spring Boot',
                                                                                                                                                                                                                                                                             'MANUAL', 20, 2, 70.00, 10.00, 'AFTER_SUBMIT', true, true, true, 'PUBLISHED',
                                                                                                                                                                                                                                                                             now() - interval '25 days', now() - interval '28 days', now()),

                                                                                                                                                                                                                                                                            ('60000000-0000-0000-0000-000000000002',
                                                                                                                                                                                                                                                                             '20000000-0000-0000-0000-000000000002',
                                                                                                                                                                                                                                                                             '00000000-0000-0000-0000-000000000002',
                                                                                                                                                                                                                                                                             'Quiz: Index & Query Optimization', 'Kiểm tra kiến thức về Index PostgreSQL',
                                                                                                                                                                                                                                                                             'MANUAL', 15, 1, 60.00, 10.00, 'AFTER_DEADLINE', false, false, false, 'PUBLISHED',
                                                                                                                                                                                                                                                                             now() - interval '18 days', now() - interval '20 days', now());

-- Bank questions cho quiz 1
INSERT INTO bank_questions (id, course_id, created_by, subject_tag, question_text, question_type, difficulty, points, status, created_at) VALUES
                                                                                                                                              ('61000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
                                                                                                                                               'REST', 'HTTP method nào được dùng để tạo mới một resource?', 'SINGLE_CHOICE', 'EASY', 2.00, 'ACTIVE', now()),
                                                                                                                                              ('61000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
                                                                                                                                               'REST', '@RestController khác @Controller ở điểm nào?', 'SINGLE_CHOICE', 'MEDIUM', 3.00, 'ACTIVE', now()),
                                                                                                                                              ('61000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
                                                                                                                                               'REST', 'Các HTTP status code nào sau đây là 2xx Success?', 'MULTIPLE_CHOICE', 'MEDIUM', 5.00, 'ACTIVE', now());

INSERT INTO bank_options (id, bank_question_id, option_text, is_correct, order_index) VALUES
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000001', 'GET',    false, 1),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000001', 'POST',   true,  2),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000001', 'PUT',    false, 3),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000001', 'DELETE', false, 4),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000002', '@RestController tự động thêm @ResponseBody vào mọi method', true,  1),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000002', '@RestController không hỗ trợ Thymeleaf',                   false, 2),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000002', '@RestController là alias của @Controller',                  false, 3),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000002', 'Không có sự khác biệt',                                    false, 4),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000003', '200 OK',      true,  1),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000003', '201 Created', true,  2),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000003', '404 Not Found',false, 3),
                                                                                          (gen_random_uuid(), '61000000-0000-0000-0000-000000000003', '204 No Content',true, 4);

-- Quiz questions (snapshot từ bank)
INSERT INTO quiz_questions (id, quiz_id, bank_question_id, question_text, question_type, points, order_index) VALUES
                                                                                                                  ('62000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000001',
                                                                                                                   'HTTP method nào được dùng để tạo mới một resource?', 'SINGLE_CHOICE', 2.00, 1),
                                                                                                                  ('62000000-0000-0000-0000-000000000002', '60000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000002',
                                                                                                                   '@RestController khác @Controller ở điểm nào?', 'SINGLE_CHOICE', 3.00, 2),
                                                                                                                  ('62000000-0000-0000-0000-000000000003', '60000000-0000-0000-0000-000000000001', '61000000-0000-0000-0000-000000000003',
                                                                                                                   'Các HTTP status code nào sau đây là 2xx Success?', 'MULTIPLE_CHOICE', 5.00, 3);

INSERT INTO quiz_options (id, question_id, option_text, is_correct, order_index) VALUES
                                                                                     ('63000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000001', 'GET',    false, 1),
                                                                                     ('63000000-0000-0000-0000-000000000002', '62000000-0000-0000-0000-000000000001', 'POST',   true,  2),
                                                                                     ('63000000-0000-0000-0000-000000000003', '62000000-0000-0000-0000-000000000001', 'PUT',    false, 3),
                                                                                     ('63000000-0000-0000-0000-000000000004', '62000000-0000-0000-0000-000000000001', 'DELETE', false, 4),
                                                                                     ('63000000-0000-0000-0000-000000000005', '62000000-0000-0000-0000-000000000002', '@RestController tự động thêm @ResponseBody vào mọi method', true,  1),
                                                                                     ('63000000-0000-0000-0000-000000000006', '62000000-0000-0000-0000-000000000002', '@RestController không hỗ trợ Thymeleaf',                   false, 2),
                                                                                     ('63000000-0000-0000-0000-000000000007', '62000000-0000-0000-0000-000000000002', '@RestController là alias của @Controller',                  false, 3),
                                                                                     ('63000000-0000-0000-0000-000000000008', '62000000-0000-0000-0000-000000000002', 'Không có sự khác biệt',                                    false, 4),
                                                                                     ('63000000-0000-0000-0000-000000000009', '62000000-0000-0000-0000-000000000003', '200 OK',         true,  1),
                                                                                     ('63000000-0000-0000-0000-000000000010', '62000000-0000-0000-0000-000000000003', '201 Created',    true,  2),
                                                                                     ('63000000-0000-0000-0000-000000000011', '62000000-0000-0000-0000-000000000003', '404 Not Found',  false, 3),
                                                                                     ('63000000-0000-0000-0000-000000000012', '62000000-0000-0000-0000-000000000003', '204 No Content', true,  4);

-- Proctoring config cho quiz 1
INSERT INTO proctoring_configs (id, course_id, max_violations, warning_threshold_1, warning_threshold_2, auto_submit_message, updated_by, updated_at) VALUES
    (gen_random_uuid(), '20000000-0000-0000-0000-000000000001', 3, 1, 2,
     'Bạn đã vi phạm quá số lần cho phép. Bài làm sẽ được nộp tự động.', '00000000-0000-0000-0000-000000000002', now());

-- Quiz attempt: student1 làm quiz 1 (passed)
INSERT INTO quiz_attempts (id, quiz_id, student_id, course_id, attempt_number, status, score, score_percentage, is_passed, correct_count, incorrect_count, unanswered_count, auto_submitted, proctoring_enabled, violation_count, started_at, submitted_at, time_spent_seconds, ip_address) VALUES
    ('64000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000004',
     '20000000-0000-0000-0000-000000000001',
     1, 'GRADED', 10.00, 100.00, true, 3, 0, 0, false, true, 0,
     now() - interval '19 days', now() - interval '19 days' + interval '15 minutes', 900, '127.0.0.1');

INSERT INTO quiz_attempt_answers (id, attempt_id, question_id, selected_option_ids, is_correct, points_earned, answered_at) VALUES
                                                                                                                                (gen_random_uuid(), '64000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000001',
                                                                                                                                 '["63000000-0000-0000-0000-000000000002"]', true, 2.00, now() - interval '19 days'),
                                                                                                                                (gen_random_uuid(), '64000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000002',
                                                                                                                                 '["63000000-0000-0000-0000-000000000005"]', true, 3.00, now() - interval '19 days'),
                                                                                                                                (gen_random_uuid(), '64000000-0000-0000-0000-000000000001', '62000000-0000-0000-0000-000000000003',
                                                                                                                                 '["63000000-0000-0000-0000-000000000009","63000000-0000-0000-0000-000000000010","63000000-0000-0000-0000-000000000012"]',
                                                                                                                                 true, 5.00, now() - interval '19 days');

-- ============================================================
-- 12. ASSIGNMENTS
-- ============================================================
INSERT INTO assignments (id, course_id, created_by, title, description, status, scope, deadline, allow_late_submission, late_penalty_percent, max_score, max_file_size_mb, allowed_file_types, max_submissions, published_at, created_at, updated_at) VALUES
                                                                                                                                                                                                                                                          ('70000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                                                                           '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                                                                           '00000000-0000-0000-0000-000000000002',
                                                                                                                                                                                                                                                           'Bài tập: Xây dựng CRUD API với Spring Boot',
                                                                                                                                                                                                                                                           'Xây dựng REST API quản lý sản phẩm với đầy đủ CRUD, validation và exception handling.',
                                                                                                                                                                                                                                                           'PUBLISHED', 'ALL',
                                                                                                                                                                                                                                                           now() + interval '7 days', true, 20, 100.00, 50,
                                                                                                                                                                                                                                                           '["zip","jar","pdf"]', 2,
                                                                                                                                                                                                                                                           now() - interval '20 days', now() - interval '22 days', now()),

                                                                                                                                                                                                                                                          ('70000000-0000-0000-0000-000000000002',
                                                                                                                                                                                                                                                           '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                                                                           '00000000-0000-0000-0000-000000000002',
                                                                                                                                                                                                                                                           'Bài tập nhóm: Implement JWT Authentication',
                                                                                                                                                                                                                                                           'Nhóm Buổi Tối implement JWT login/logout/refresh token.',
                                                                                                                                                                                                                                                           'PUBLISHED', 'GROUP',
                                                                                                                                                                                                                                                           now() + interval '14 days', false, 0, 100.00, 100,
                                                                                                                                                                                                                                                           '["zip","pdf"]', 1,
                                                                                                                                                                                                                                                           now() - interval '15 days', now() - interval '16 days', now());

INSERT INTO assignment_groups (id, assignment_id, group_id, assigned_at) VALUES
    (gen_random_uuid(), '70000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', now() - interval '15 days');

-- Submission của student1
INSERT INTO assignment_submissions (id, assignment_id, student_id, course_id, submission_number, status, note, is_late, score, feedback, graded_by, graded_at, submitted_at, created_at) VALUES
    ('71000000-0000-0000-0000-000000000001',
     '70000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000004',
     '20000000-0000-0000-0000-000000000001',
     1, 'GRADED', 'Em đã implement đầy đủ CRUD và thêm pagination.', false,
     92.00, 'Làm tốt! Code sạch, có unit test. Cần cải thiện thêm error message.',
     '00000000-0000-0000-0000-000000000002',
     now() - interval '16 days',
     now() - interval '18 days', now() - interval '18 days');

INSERT INTO submission_files (id, submission_id, original_filename, s3_key, file_size_bytes, mime_type, extension, scan_status, order_index, uploaded_at) VALUES
    (gen_random_uuid(), '71000000-0000-0000-0000-000000000001',
     'spring-crud-api.zip', 'submissions/71000000/spring-crud-api.zip',
     2048000, 'application/zip', 'zip', 'CLEAN', 1, now() - interval '18 days');

-- ============================================================
-- 13. FORUM POSTS & REPLIES
-- ============================================================
INSERT INTO forum_posts (id, course_id, author_id, title, content, is_pinned, reply_count, created_at, updated_at) VALUES
                                                                                                                       ('80000000-0000-0000-0000-000000000001',
                                                                                                                        '20000000-0000-0000-0000-000000000001',
                                                                                                                        '00000000-0000-0000-0000-000000000002',
                                                                                                                        '[Thông báo] Lịch học tuần này', 'Lịch học tuần này có thay đổi: buổi thứ 4 dời sang thứ 5 cùng giờ.', true, 2,
                                                                                                                        now() - interval '5 days', now() - interval '5 days'),

                                                                                                                       ('80000000-0000-0000-0000-000000000002',
                                                                                                                        '20000000-0000-0000-0000-000000000001',
                                                                                                                        '00000000-0000-0000-0000-000000000004',
                                                                                                                        'Hỏi về @Transactional trong Spring',
                                                                                                                        '@Transactional có tự động rollback khi gặp RuntimeException không? Mình test thấy đôi khi không rollback.', false, 1,
                                                                                                                        now() - interval '3 days', now() - interval '3 days');

INSERT INTO forum_replies (id, post_id, course_id, author_id, content, created_at, updated_at) VALUES
                                                                                                   (gen_random_uuid(), '80000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                                                                                                    '00000000-0000-0000-0000-000000000005',
                                                                                                    'Cảm ơn thầy đã thông báo ạ!', now() - interval '4 days', now() - interval '4 days'),
                                                                                                   (gen_random_uuid(), '80000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                                                                                                    '00000000-0000-0000-0000-000000000006',
                                                                                                    'Em đã ghi nhận ạ.', now() - interval '4 days', now() - interval '4 days'),
                                                                                                   (gen_random_uuid(), '80000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
                                                                                                    '00000000-0000-0000-0000-000000000002',
                                                                                                    '@Transactional mặc định chỉ rollback với unchecked exception (RuntimeException). Với checked exception bạn cần thêm rollbackFor = Exception.class.',
                                                                                                    now() - interval '2 days', now() - interval '2 days');

-- ============================================================
-- 14. CHAT ROOMS & MESSAGES
-- ============================================================
INSERT INTO chat_rooms (id, name, room_type, course_id, group_id, created_by, is_active, last_message_at, created_at) VALUES
                                                                                                                          ('90000000-0000-0000-0000-000000000001',
                                                                                                                           'Chat khoá Spring Boot', 'COURSE', '20000000-0000-0000-0000-000000000001', NULL,
                                                                                                                           '00000000-0000-0000-0000-000000000002', true, now() - interval '1 hour', now() - interval '29 days'),
                                                                                                                          ('90000000-0000-0000-0000-000000000002',
                                                                                                                           'Nhóm Spring Boot - Buổi Tối', 'GROUP', '20000000-0000-0000-0000-000000000001',
                                                                                                                           '50000000-0000-0000-0000-000000000001',
                                                                                                                           '00000000-0000-0000-0000-000000000002', true, now() - interval '2 hours', now() - interval '19 days');

INSERT INTO chat_room_members (id, room_id, user_id, role, joined_at) VALUES
                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'MODERATOR', now() - interval '29 days'),
                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', 'MEMBER',    now() - interval '25 days'),
                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005', 'MEMBER',    now() - interval '24 days'),
                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000006', 'MEMBER',    now() - interval '22 days'),
                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'MODERATOR', now() - interval '19 days'),
                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004', 'MEMBER',    now() - interval '19 days'),
                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005', 'MEMBER',    now() - interval '18 days');

INSERT INTO chat_messages (id, room_id, sender_id, message_type, content, created_at) VALUES
                                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'TEXT',
                                                                                           'Chào mừng các bạn đến với khoá Spring Boot! Có câu hỏi gì cứ hỏi nhé.', now() - interval '29 days'),
                                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', 'TEXT',
                                                                                           'Thầy ơi, bài 2 em cài Maven nhưng bị lỗi dependency. Thầy có thể hỗ trợ không ạ?', now() - interval '3 hours'),
                                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'TEXT',
                                                                                           'Bạn gửi stacktrace lên đây nhé, mình xem cho.', now() - interval '2 hours' - interval '30 minutes'),
                                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'TEXT',
                                                                                           'Nhóm tối nay ôn lại phần JWT nhé, có quiz vào tuần sau.', now() - interval '2 hours'),
                                                                                          (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004', 'TEXT',
                                                                                           'Dạ vâng thầy!', now() - interval '1 hour');

-- ============================================================
-- 15. AI CONVERSATIONS & MESSAGES
-- ============================================================
INSERT INTO ai_conversations (id, student_id, course_id, lesson_id, title, status, message_count, created_at, last_message_at) VALUES
    ('a0000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000004',
     '20000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000005',
     'Hỏi về Spring Security Filter Chain', 'ACTIVE', 4,
     now() - interval '2 days', now() - interval '1 day');

INSERT INTO ai_messages (id, conversation_id, role, content, llm_provider, llm_model, response_time_ms, created_at) VALUES
                                                                                                                        (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'user',
                                                                                                                         'Spring Security Filter Chain là gì và hoạt động thế nào?',
                                                                                                                         NULL, NULL, NULL, now() - interval '2 days'),
                                                                                                                        (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'assistant',
                                                                                                                         'Spring Security Filter Chain là một chuỗi các Filter được thực thi theo thứ tự khi có HTTP request đến. Mỗi filter có thể xử lý authentication, authorization hoặc các security concern khác. Trong khoá học này, chúng ta cấu hình SecurityFilterChain bean để định nghĩa các rule bảo mật cho từng endpoint.',
                                                                                                                         'openai', 'gpt-4o-mini', 1240, now() - interval '2 days'),
                                                                                                                        (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'user',
                                                                                                                         'Vậy JWT được kiểm tra ở filter nào?',
                                                                                                                         NULL, NULL, NULL, now() - interval '1 day'),
                                                                                                                        (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'assistant',
                                                                                                                         'JWT được kiểm tra trong custom filter thường đặt trước UsernamePasswordAuthenticationFilter. Bạn tạo một class extends OncePerRequestFilter, override doFilterInternal để extract token từ header Authorization, validate và set Authentication vào SecurityContext.',
                                                                                                                         'openai', 'gpt-4o-mini', 980, now() - interval '1 day');

-- ============================================================
-- 16. CERTIFICATES (student1 hoàn thành course 1)
-- ============================================================
INSERT INTO certificates (id, student_id, course_id, credential_id, pdf_s3_key, pdf_url, status, issued_at) VALUES
    (gen_random_uuid(),
     '00000000-0000-0000-0000-000000000004',
     '20000000-0000-0000-0000-000000000001',
     'RIKKEI-2024-001234',
     'certificates/RIKKEI-2024-001234.pdf',
     'https://s3.amazonaws.com/rikkei-edu/certificates/RIKKEI-2024-001234.pdf',
     'ISSUED',
     now() - interval '18 days');

-- ============================================================
-- 17. NOTIFICATIONS
-- ============================================================
INSERT INTO notifications (id, recipient_id, idempotency_key, type, title, body, reference_type, reference_id, actor_id, actor_name, priority, is_read, email_sent, push_sent, created_at) VALUES
                                                                                                                                                                                               (gen_random_uuid(), '00000000-0000-0000-0000-000000000004',
                                                                                                                                                                                                'cert-issued-20000000-0000-0000-0000-000000000001-00000000-0000-0000-0000-000000000004',
                                                                                                                                                                                                'CERTIFICATE_ISSUED', '🎉 Chúc mừng bạn đã hoàn thành khoá học!',
                                                                                                                                                                                                'Chứng chỉ khoá "Java Spring Boot từ Zero đến Hero" đã được cấp. Mã chứng chỉ: RIKKEI-2024-001234.',
                                                                                                                                                                                                'CERTIFICATE', NULL, NULL, NULL, 'HIGH', true, true, false, now() - interval '18 days'),

                                                                                                                                                                                               (gen_random_uuid(), '00000000-0000-0000-0000-000000000004',
                                                                                                                                                                                                'submission-graded-71000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                'SUBMISSION_GRADED', 'Bài tập của bạn đã được chấm điểm',
                                                                                                                                                                                                'Bài tập "Xây dựng CRUD API với Spring Boot" được chấm 92/100. Xem nhận xét từ giảng viên.',
                                                                                                                                                                                                'ASSIGNMENT', '70000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                '00000000-0000-0000-0000-000000000002', 'Nguyễn Văn Minh',
                                                                                                                                                                                                'NORMAL', true, true, false, now() - interval '16 days'),

                                                                                                                                                                                               (gen_random_uuid(), '00000000-0000-0000-0000-000000000005',
                                                                                                                                                                                                'forum-reply-80000000-0000-0000-0000-000000000001-notif',
                                                                                                                                                                                                'FORUM_REPLY_ADDED', 'Có trả lời mới trong bài đăng của bạn',
                                                                                                                                                                                                'Nguyễn Văn Minh đã trả lời bài đăng "Hỏi về @Transactional trong Spring".',
                                                                                                                                                                                                'FORUM_POST', '80000000-0000-0000-0000-000000000002',
                                                                                                                                                                                                '00000000-0000-0000-0000-000000000002', 'Nguyễn Văn Minh',
                                                                                                                                                                                                'NORMAL', false, false, false, now() - interval '2 days'),

                                                                                                                                                                                               (gen_random_uuid(), '00000000-0000-0000-0000-000000000004',
                                                                                                                                                                                                'quiz-published-60000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                'QUIZ_PUBLISHED', 'Quiz mới đã được mở',
                                                                                                                                                                                                'Quiz "REST API & Spring MVC" trong khoá Spring Boot đã mở. Thời gian làm bài: 20 phút.',
                                                                                                                                                                                                'QUIZ', '60000000-0000-0000-0000-000000000001',
                                                                                                                                                                                                '00000000-0000-0000-0000-000000000002', 'Nguyễn Văn Minh',
                                                                                                                                                                                                'NORMAL', true, true, false, now() - interval '25 days');

-- ============================================================
-- 18. OUTBOX EVENTS (pending & processed examples)
-- ============================================================
INSERT INTO outbox_events (id, event_type, aggregate_type, aggregate_id, payload, status, retry_count, max_retries, scheduled_at, created_at, processed_at) VALUES
                                                                                                                                                                (gen_random_uuid(), 'ASSIGNMENT_DEADLINE_REMINDER', 'ASSIGNMENT',
                                                                                                                                                                 '70000000-0000-0000-0000-000000000001',
                                                                                                                                                                 '{"assignment_id":"70000000-0000-0000-0000-000000000001","title":"Xây dựng CRUD API với Spring Boot","recipient_scope":"ALL_ENROLLED","course_id":"20000000-0000-0000-0000-000000000001","deadline_in_hours":48}',
                                                                                                                                                                 'PENDING', 0, 3, now() + interval '5 days', now(), NULL),

                                                                                                                                                                (gen_random_uuid(), 'CERTIFICATE_ISSUED', 'CERTIFICATE',
                                                                                                                                                                 '20000000-0000-0000-0000-000000000001',
                                                                                                                                                                 '{"course_id":"20000000-0000-0000-0000-000000000001","student_id":"00000000-0000-0000-0000-000000000004","credential_id":"RIKKEI-2024-001234"}',
                                                                                                                                                                 'PROCESSED', 0, 3, now() - interval '18 days', now() - interval '18 days', now() - interval '18 days'),

                                                                                                                                                                (gen_random_uuid(), 'SUBMISSION_GRADED', 'ASSIGNMENT',
                                                                                                                                                                 '70000000-0000-0000-0000-000000000001',
                                                                                                                                                                 '{"submission_id":"71000000-0000-0000-0000-000000000001","student_id":"00000000-0000-0000-0000-000000000004","score":92,"assignment_title":"Xây dựng CRUD API với Spring Boot"}',
                                                                                                                                                                 'PROCESSED', 0, 3, now() - interval '16 days', now() - interval '16 days', now() - interval '16 days');

-- ============================================================
-- 19. AUDIT LOGS
-- ============================================================
INSERT INTO audit_logs (id, actor_id, actor_email, action, target_type, target_id, payload_after, ip_address, created_at) VALUES
                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'admin@rikkei.edu',
                                                                                                                               'COURSE_APPROVED', 'COURSE', '20000000-0000-0000-0000-000000000001',
                                                                                                                               '{"status":"PUBLISHED","approved_by":"admin@rikkei.edu"}',
                                                                                                                               '127.0.0.1', now() - interval '30 days'),

                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000002', 'instructor1@rikkei.edu',
                                                                                                                               'QUIZ_PUBLISHED', 'QUIZ', '60000000-0000-0000-0000-000000000001',
                                                                                                                               '{"title":"Quiz: REST API & Spring MVC","status":"PUBLISHED"}',
                                                                                                                               '127.0.0.1', now() - interval '25 days'),

                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000002', 'instructor1@rikkei.edu',
                                                                                                                               'SUBMISSION_GRADED', 'ASSIGNMENT_SUBMISSION', '71000000-0000-0000-0000-000000000001',
                                                                                                                               '{"score":92,"feedback":"Làm tốt! Code sạch, có unit test.","graded_by":"instructor1@rikkei.edu"}',
                                                                                                                               '127.0.0.1', now() - interval '16 days'),

                                                                                                                              (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'admin@rikkei.edu',
                                                                                                                               'USER_ENROLLED', 'COURSE_ENROLLMENT', '20000000-0000-0000-0000-000000000001',
                                                                                                                               '{"student_id":"00000000-0000-0000-0000-000000000008","enrolled_by":"admin@rikkei.edu","course_id":"20000000-0000-0000-0000-000000000001"}',
                                                                                                                               '127.0.0.1', now() - interval '18 days');

-- ============================================================
-- 20. USER PREFERENCES
-- ============================================================
INSERT INTO user_preferences (id, user_id, pref_key, pref_value) VALUES
                                                                     (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', 'ui_theme',       '"dark"'),
                                                                     (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', 'language',       '"vi"'),
                                                                     (gen_random_uuid(), '00000000-0000-0000-0000-000000000005', 'ui_theme',       '"light"'),
                                                                     (gen_random_uuid(), '00000000-0000-0000-0000-000000000005', 'email_digest',   '"daily"');

-- ============================================================
-- 21. NOTIFICATION PREFERENCES
-- ============================================================
INSERT INTO notification_preferences (id, user_id, notification_type, in_app_enabled, email_enabled, push_enabled, updated_at) VALUES
                                                                                                                                   (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', 'QUIZ_PUBLISHED',        true, true,  false, now()),
                                                                                                                                   (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', 'SUBMISSION_GRADED',     true, true,  false, now()),
                                                                                                                                   (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', 'ASSIGNMENT_PUBLISHED',  true, true,  false, now()),
                                                                                                                                   (gen_random_uuid(), '00000000-0000-0000-0000-000000000005', 'FORUM_REPLY_ADDED',     true, false, false, now()),
                                                                                                                                   (gen_random_uuid(), '00000000-0000-0000-0000-000000000005', 'QUIZ_PUBLISHED',        true, true,  false, now());

COMMIT;

-- ============================================================
-- SUMMARY
-- ============================================================
-- Users        : 1 admin, 2 instructors, 7 students
-- Courses      : 4 published, 1 draft
-- Chapters     : 10 chapters across 5 courses
-- Lessons      : 10 lessons (VIDEO + TEXT types)
-- Enrollments  : 14 enrollments across 4 courses
-- Study groups : 3 groups, 7 memberships
-- Quizzes      : 2 quizzes (with questions, options, 1 attempt)
-- Assignments  : 2 assignments (1 graded submission)
-- Forum        : 2 posts, 3 replies
-- Chat rooms   : 2 rooms (COURSE + GROUP), 5 messages
-- AI chat      : 1 conversation, 4 messages
-- Certificates : 1 issued
-- Notifications: 4 notifications
-- Outbox events: 3 events (1 pending, 2 processed)
-- Audit logs   : 4 entries
-- ============================================================
