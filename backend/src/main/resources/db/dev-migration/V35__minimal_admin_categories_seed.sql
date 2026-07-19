-- ============================================================
-- RIKKEI EDU LMS - MINIMAL DEV FOUNDATION SEED
-- Xóa toàn bộ dữ liệu nghiệp vụ và nạp dữ liệu nền tối thiểu:
-- 1 admin + 3 giảng viên, 10 danh mục, 24 khóa học PUBLISHED.
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
    '$2b$12$a6fbTUMo6AmPJryeuC1voeh1rMM621E9T9WNBSWkAExKJxOembv3a',
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
-- INSTRUCTORS (3 giảng viên)
-- Mật khẩu mặc định: 123456 (giống admin)
-- ============================================================
INSERT INTO users (id, email, full_name, password_hash, role, status, phone_number, bio, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000002', 'vuhoangchinh@rikkei.edu', 'Vũ Hoàng Chính',
     '$2b$12$a6fbTUMo6AmPJryeuC1voeh1rMM621E9T9WNBSWkAExKJxOembv3a', 'INSTRUCTOR', 'ACTIVE', '0990000001',
     'Giảng viên Backend & Database với hơn 10 năm kinh nghiệm trong phát triển ứng dụng doanh nghiệp.',
     now(), now()),
    ('00000000-0000-0000-0000-000000000003', 'vuthanhthao@rikkei.edu', 'Vũ Thanh Thảo',
     '$2b$12$a6fbTUMo6AmPJryeuC1voeh1rMM621E9T9WNBSWkAExKJxOembv3a', 'INSTRUCTOR', 'ACTIVE', '0990000002',
     'Giảng viên Frontend & Mobile, chuyên sâu về React, Vue, và Flutter.',
     now(), now()),
    ('00000000-0000-0000-0000-000000000004', 'trinhcongvu@rikkei.edu', 'Trịnh Công Vũ',
     '$2b$12$a6fbTUMo6AmPJryeuC1voeh1rMM621E9T9WNBSWkAExKJxOembv3a', 'INSTRUCTOR', 'ACTIVE', '0990000004',
     'Giảng viên AI & System Design, nghiên cứu sâu về Machine Learning và Kiến trúc phần mềm.',
     now(), now());

-- ============================================================
-- COURSES (24 khóa học PUBLISHED, 8 khóa / giảng viên)
-- ============================================================
INSERT INTO courses (id, instructor_id, category_id, title, slug, description, level, status, chat_enabled, created_at, updated_at) VALUES
-- >>> Vũ Hoàng Chính (instructor_id = ...002) - 8 courses <<<
('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
 'Java Core Fundamentals', 'java-core-fundamentals',
 'Nắm vững nền tảng Java từ cú pháp cơ bản đến OOP, Collections, Exception Handling và Stream API. Phù hợp cho người mới bắt đầu với lập trình Java.',
 'BEGINNER', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
 'Spring Boot REST API Development', 'spring-boot-rest-api-development',
 'Xây dựng RESTful API chuyên nghiệp với Spring Boot: JPA, Security, Validation, Exception Handling và documenting với Swagger.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003',
 'PostgreSQL for Developers', 'postgresql-for-developers',
 'Làm chủ PostgreSQL từ thiết kế schema, indexing, query optimization đến stored procedures và advanced PostgreSQL features.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003',
 'MongoDB & NoSQL Database Design', 'mongodb-nosql-database-design',
 'Tìm hiểu MongoDB: document model, aggregation pipeline, indexing strategies và thiết kế schema cho ứng dụng thực tế.',
 'BEGINNER', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004',
 'Docker Containerization & Orchestration', 'docker-containerization-orchestration',
 'Thực hành Docker từ cơ bản đến nâng cao: image building, compose, networking, volumes và giới thiệu Kubernetes orchestration.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004',
 'CI/CD Pipeline Automation', 'cicd-pipeline-automation',
 'Xây dựng pipeline CI/CD hoàn chỉnh với Jenkins, GitLab CI và GitHub Actions: build, test, deploy tự động cho dự án thực tế.',
 'ADVANCED', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
 'Microservices Architecture with Spring Cloud', 'microservices-spring-cloud',
 'Thiết kế và triển khai hệ thống microservices với Spring Cloud: service discovery, API gateway, circuit breaker, distributed tracing.',
 'ADVANCED', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003',
 'Redis & Caching Solutions', 'redis-caching-solutions',
 'Tận dụng Redis cho caching, session management, message queue và real-time data processing trong ứng dụng hiệu suất cao.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

-- >>> Vũ Thanh Thảo (instructor_id = ...003) - 8 courses <<<
('20000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002',
 'HTML5 & CSS3 Responsive Web Design', 'html5-css3-responsive-web-design',
 'Xây dựng giao diện web chuẩn với HTML5 semantic, CSS3 Flexbox/Grid, responsive design và CSS animation.',
 'BEGINNER', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002',
 'ReactJS Modern Web Applications', 'reactjs-modern-web-applications',
 'Làm chủ ReactJS: hooks, context API, state management với Redux Toolkit, React Router và performance optimization.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002',
 'Vue.js 3 & Composition API', 'vuejs-3-composition-api',
 'Phát triển ứng dụng với Vue.js 3: Composition API, Pinia store, Vue Router, và server-side rendering với Nuxt.js.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000005',
 'React Native Mobile Development', 'react-native-mobile-development',
 'Xây dựng ứng dụng di động đa nền tảng với React Native: navigation, state management, native modules và app store deployment.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000005',
 'Flutter for Cross-Platform Apps', 'flutter-cross-platform-apps',
 'Phát triển ứng dụng di động và web với Flutter: widget tree, state management (Riverpod, Bloc), Firebase integration và CI/CD.',
 'BEGINNER', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000008',
 'Unit Testing with Jest & React Testing Library', 'unit-testing-jest-react-testing-library',
 'Viết unit test chất lượng cao với Jest và React Testing Library: mock, async testing, coverage và testing best practices.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002',
 'Advanced TypeScript Design Patterns', 'advanced-typescript-design-patterns',
 'Khai thác TypeScript nâng cao: generic types, conditional types, mapped types, decorators và design patterns trong TypeScript.',
 'ADVANCED', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000008',
 'Selenium WebDriver & Test Automation', 'selenium-webdriver-test-automation',
 'Tự động hóa kiểm thử web với Selenium WebDriver: page object model, data-driven testing, parallel execution và CI integration.',
 'ADVANCED', 'PUBLISHED', true, now(), now()),

-- >>> Trịnh Công Vũ (instructor_id = ...004) - 8 courses <<<
('20000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000006',
 'Python for Data Science & Analytics', 'python-data-science-analytics',
 'Bắt đầu với Data Science: NumPy, Pandas, Matplotlib, Seaborn và thực hành phân tích dữ liệu với dataset thực tế.',
 'BEGINNER', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000018', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000006',
 'Machine Learning with scikit-learn & Python', 'machine-learning-scikit-learn',
 'Xây dựng mô hình Machine Learning: regression, classification, clustering, ensemble methods và model evaluation với scikit-learn.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000019', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000006',
 'Deep Learning & Neural Networks with TensorFlow', 'deep-learning-tensorflow',
 'Đi sâu vào Deep Learning: CNN, RNN, LSTM, Transformers và triển khai mô hình với TensorFlow & Keras.',
 'ADVANCED', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000007',
 'Big Data Processing with Apache Spark', 'big-data-apache-spark',
 'Xử lý dữ liệu lớn với Apache Spark: RDD, DataFrame, Spark SQL, Spark Streaming và tối ưu hiệu năng cluster.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000009',
 'Software Architecture & System Design', 'software-architecture-system-design',
 'Thiết kế hệ thống phần mềm: clean architecture, domain-driven design, event-driven architecture, CQRS và các mẫu kiến trúc hiện đại.',
 'ADVANCED', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000022', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000010',
 'Web Security & OWASP Top 10', 'web-security-owasp-top-10',
 'Bảo mật ứng dụng web: OWASP Top 10, SQL injection, XSS, CSRF, authentication bypass và các biện pháp phòng chống.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000023', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000010',
 'Ethical Hacking & Penetration Testing', 'ethical-hacking-penetration-testing',
 'Thực hành penetration testing: reconnaissance, vulnerability scanning, exploitation, post-exploitation và báo cáo lỗ hổng.',
 'BEGINNER', 'PUBLISHED', true, now(), now()),

('20000000-0000-0000-0000-000000000024', '00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000007',
 'Data Warehouse & ETL Pipeline Design', 'data-warehouse-etl-pipeline-design',
 'Thiết kế Data Warehouse: dimensional modeling, ETL pipeline với Apache Airflow, data quality và reporting với BI tools.',
 'INTERMEDIATE', 'PUBLISHED', true, now(), now());

-- ============================================================
-- SUMMARY
-- Users: 1 admin + 3 instructors
-- Course categories: 10
-- Courses: 24 (PUBLISHED)
-- Lessons, groups, assignments, quizzes, notifications: 0
-- ============================================================
