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
-- USERS: 1 admin + 3 giảng viên
-- ============================================================
INSERT INTO users (id, email, full_name, password_hash, role, status, phone_number, bio, created_at, updated_at) VALUES
                                                                                                                     ('00000000-0000-0000-0000-000000000001', 'admin@rikkei.edu', 'Vũ Xuân Cảnh',
                                                                                                                      '$2b$12$a6fbTUMo6AmPJryeuC1voeh1rMM621E9T9WNBSWkAExKJxOembv3a',
                                                                                                                      'ADMIN', 'ACTIVE', '0900000001',
                                                                                                                      'Quản trị viên hệ thống LMS Rikkei Education.',
                                                                                                                      now(), now()),
                                                                                                                     ('00000000-0000-0000-0000-000000000002', 'vuhoangchinh@rikkei.edu', 'Vũ Hoàng Chính',
                                                                                                                      '$2b$12$a6fbTUMo6AmPJryeuC1voeh1rMM621E9T9WNBSWkAExKJxOembv3a',
                                                                                                                      'INSTRUCTOR', 'ACTIVE', '0990000001',
                                                                                                                      'Giảng viên Backend & Database với hơn 10 năm kinh nghiệm.',
                                                                                                                      now(), now()),
                                                                                                                     ('00000000-0000-0000-0000-000000000003', 'vuthanhthao@rikkei.edu', 'Vũ Thanh Thảo',
                                                                                                                      '$2b$12$a6fbTUMo6AmPJryeuC1voeh1rMM621E9T9WNBSWkAExKJxOembv3a',
                                                                                                                      'INSTRUCTOR', 'ACTIVE', '0990000002',
                                                                                                                      'Giảng viên Frontend & Mobile, chuyên sâu về React, Vue và Flutter.',
                                                                                                                      now(), now()),
                                                                                                                     ('00000000-0000-0000-0000-000000000004', 'trinhcongvu@rikkei.edu', 'Trịnh Công Vũ',
                                                                                                                      '$2b$12$a6fbTUMo6AmPJryeuC1voeh1rMM621E9T9WNBSWkAExKJxOembv3a',
                                                                                                                      'INSTRUCTOR', 'ACTIVE', '0990000004',
                                                                                                                      'Giảng viên AI & System Design, Machine Learning và Kiến trúc phần mềm.',
                                                                                                                      now(), now());

-- ============================================================
-- COURSE CATEGORIES: 10 danh mục
-- ============================================================
INSERT INTO course_categories (id, name, slug, is_active, created_at) VALUES
                                                                          ('10000000-0000-0000-0000-000000000001', 'Backend Development', 'backend-development', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000002', 'Frontend Development', 'frontend-development', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000003', 'Database', 'database', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000004', 'DevOps & Cloud', 'devops-cloud', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000005', 'Mobile Development', 'mobile-development', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000006', 'AI & Machine Learning', 'ai-machine-learning', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000007', 'Data Engineering', 'data-engineering', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000008', 'Software Testing', 'software-testing', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000009', 'System Design', 'system-design', true, now()),
                                                                          ('10000000-0000-0000-0000-000000000010', 'Cybersecurity', 'cybersecurity', true, now());

-- ============================================================
-- COURSES: 24 khóa học DRAFT
-- ============================================================
WITH course_seed (idx, cat_no, inst_no, title, slug, level, descr, outcomes, prereqs) AS (
    VALUES
        -- Vũ Hoàng Chính (inst_no=2): Backend, Database, System Design
        (1, 1, 2, 'Java Spring Boot từ Zero đến Hero', 'java-spring-boot-zero-to-hero', 'INTERMEDIATE',
         'Xây dựng REST API, Spring Security, JPA, transaction và deploy backend thực chiến.',
         '["Xây dựng RESTful API với Spring Boot","Tích hợp Spring Security & JWT","Sử dụng JPA/Hibernate truy vấn CSDL","Viết unit test với Spring Boot Test","Deploy ứng dụng Spring Boot"]',
         '["Đã học qua Java Core (OOP, Collection, Exception)","Có máy tính cài JDK 17+"]'),
        (2, 1, 2, 'REST API Design với Spring Boot', 'rest-api-design-spring-boot', 'BEGINNER',
         'Thiết kế API chuẩn REST, validation, exception handling, pagination và versioning.',
         '["Thiết kế endpoint REST theo chuẩn","Xử lý validation yêu cầu đầu vào","Áp dụng pagination, sort, filter","Viết tài liệu API với Swagger"]',
         '["Biết Java cơ bản","Đã cài Spring Boot"]'),
        (9, 3, 2, 'PostgreSQL Nâng Cao và Query Optimization', 'postgresql-nang-cao-query-optimization', 'ADVANCED',
         'Index, execution plan, partitioning, replication và tối ưu hiệu năng truy vấn.',
         '["Đọc và phân tích Execution Plan","Chọn loại Index phù hợp","Thiết kế Partition chia dữ liệu","Cài đặt Streaming Replication"]',
         '["Viết thành thạo SQL","Hiểu về B-Tree index","Đã quản trị PostgreSQL cơ bản"]'),
        (23, 9, 2, 'System Design cho Backend Developer', 'system-design-backend-developer', 'ADVANCED',
         'Cache, queue, sharding, consistency, observability và thiết kế hệ thống lớn.',
         '["Thiết kế hệ thống chịu tải cao","Áp dụng caching (CDN, Redis, Memcached)","Sharding & partition database","Đảm bảo consistency & availability"]',
         '["Đã có 3+ năm kinh nghiệm backend","Hiểu về distributed system cơ bản"]'),
        -- Vũ Thanh Thảo (inst_no=3): Frontend, Mobile
        (5, 2, 3, 'ReactJS và TailwindCSS Thực Chiến', 'reactjs-tailwindcss-thuc-chien', 'BEGINNER',
         'Xây dựng SPA hiện đại với React, hooks, routing, form và TailwindCSS.',
         '["Xây dựng component với React hook","Quản lý state với Context & useReducer","Routing đa trang với React Router","Thiết kế UI responsive với TailwindCSS"]',
         '["Biết HTML, CSS, JavaScript cơ bản","Đã cài Node.js và npm"]'),
        (6, 2, 3, 'Next.js Fullstack App Router', 'nextjs-fullstack-app-router', 'INTERMEDIATE',
         'Server components, routing, data fetching, authentication và deployment với Next.js.',
         '["Hiểu SSR, SSG, ISR trong Next.js","Xây dựng Server & Client Component","API Routes và Server Actions","Authentication với NextAuth.js"]',
         '["Biết ReactJS cơ bản","Hiểu về REST API","Đã từng làm SPA với React"]'),
        (7, 2, 3, 'TypeScript Nâng Cao cho Frontend', 'typescript-nang-cao-frontend', 'INTERMEDIATE',
         'Generic, utility type, type guard, API typing và design reusable component.',
         '["Sử dụng Generic type linh hoạt","Tạo Utility type & Conditional type","Viết type guard an toàn","Typing API response & request"]',
         '["Biết TypeScript cơ bản","Đã làm việc với React","Hiểu về JavaScript ES6+"]'),
        (8, 2, 3, 'Vue 3 và Pinia Thực Chiến', 'vue-3-pinia-thuc-chien', 'BEGINNER',
         'Composition API, Pinia, Vue Router và xây dựng dashboard quản trị.',
         '["Viết component với Composition API","Quản lý state với Pinia store","Routing phức tạp với Vue Router","Call API và xử lý bất đồng bộ"]',
         '["Biết HTML, CSS, JavaScript","Hiểu cơ bản về SPA"]'),
        (15, 5, 3, 'Flutter Mobile App Căn Bản', 'flutter-mobile-app-can-ban', 'BEGINNER',
         'Widget, navigation, state management, API integration và build mobile app.',
         '["Xây dựng UI với Widget tree","Điều hướng giữa các màn hình","Quản lý state với Provider","Tích hợp REST API","Build APK/IPA release"]',
         '["Biết lập trình cơ bản (bất kỳ ngôn ngữ)","Có máy tính cài Flutter SDK"]'),
        (16, 5, 3, 'React Native Thực Chiến', 'react-native-thuc-chien', 'INTERMEDIATE',
         'Navigation, native module, offline storage và publish app với React Native.',
         '["Xây dựng navigation phức tạp","Gọi native module (Camera, GPS)","Xử lý offline với AsyncStorage","Push notification với Firebase"]',
         '["Biết ReactJS","Đã cài Node.js và React Native CLI"]'),
        -- Trịnh Công Vũ (inst_no=4): Backend nâng cao, DevOps, AI, Data, Testing, Security
        (3, 1, 4, 'Microservices với Spring Cloud', 'microservices-spring-cloud', 'ADVANCED',
         'Service discovery, API gateway, config server, circuit breaker, distributed tracing.',
         '["Thiết kế kiến trúc microservices","Xây dựng Eureka Service Discovery","Cài đặt API Gateway với Spring Cloud Gateway","Tích hợp Circuit Breaker Resilience4j"]',
         '["Thành thạo Spring Boot REST API","Hiểu về message queue (RabbitMQ/Kafka)","Đã có kinh nghiệm làm dự án thực tế"]'),
        (4, 1, 4, 'Node.js Backend với NestJS', 'nodejs-backend-nestjs', 'INTERMEDIATE',
         'Xây dựng backend modular với NestJS, TypeORM, authentication và testing.',
         '["Hiểu kiến trúc module trong NestJS","Xây dựng REST & GraphQL API","Tích hợp TypeORM + PostgreSQL","JWT Authentication & Role-based Authorization"]',
         '["Biết TypeScript cơ bản","Đã từng làm việc với Node.js/Express","Hiểu về REST API"]'),
        (10, 3, 4, 'MySQL Performance Tuning', 'mysql-performance-tuning', 'INTERMEDIATE',
         'Tối ưu schema, index, transaction isolation và slow query trong MySQL.',
         '["Tối ưu schema & kiểu dữ liệu","Sử dụng Explain để phát hiện vấn đề","Cài đặt transaction isolation phù hợp","Bật slow query log và phân tích"]',
         '["Viết SQL thành thạo","Biết cơ bản về MySQL admin","Đã từng gặp query chậm"]'),
        (11, 3, 4, 'Redis cho Backend Developer', 'redis-backend-developer', 'INTERMEDIATE',
         'Cache strategy, distributed lock, pub/sub, rate limit và session storage với Redis.',
         '["Hiểu kiến trúc dữ liệu Redis","Thiết kế cache strategy (LRU, TTL)","Implement distributed lock Redlock","Xây dựng pub/sub realtime notification"]',
         '["Biết Java hoặc Node.js","Hiểu về memory cache cơ bản"]'),
        (12, 4, 4, 'Docker và Kubernetes Cơ Bản', 'docker-kubernetes-co-ban', 'INTERMEDIATE',
         'Container hóa ứng dụng, Docker Compose, Kubernetes workload và service discovery.',
         '["Viết Dockerfile tối ưu","Orchestrate đa container với Compose","Tạo Kubernetes Deployment & Service","Cấu hình ConfigMap & Secret"]',
         '["Biết Linux cơ bản (terminal, file system)","Đã cài Docker Desktop"]'),
        (13, 4, 4, 'CI/CD với GitHub Actions', 'cicd-voi-github-actions', 'BEGINNER',
         'Tự động hóa build, test, scan, deploy và release pipeline cho dự án web.',
         '["Viết workflow YAML cho CI","Chạy unit test & lint tự động","Quét security vulnerability","Deploy lên staging/production"]',
         '["Biết Git cơ bản (branch, PR)","Có dự án mã nguồn mở trên GitHub"]'),
        (14, 4, 4, 'AWS cho Developer', 'aws-cho-developer', 'INTERMEDIATE',
         'EC2, S3, RDS, IAM, CloudWatch và kiến trúc cloud cơ bản cho developer.',
         '["Khởi tạo EC2 và SSH vào server","Xử lý file với S3 bucket","Setup RDS PostgreSQL & kết nối an toàn","Quản lý quyền với IAM Role & Policy"]',
         '["Biết Linux cơ bản","Đã deploy ứng dụng web"]'),
        (17, 6, 4, 'Machine Learning Căn Bản với Python', 'machine-learning-python-can-ban', 'BEGINNER',
         'Tiền xử lý dữ liệu, supervised learning, model evaluation và scikit-learn.',
         '["Tiền xử lý & trực quan dữ liệu với Pandas/Matplotlib","Xây dựng Linear/Logistic Regression","Hiểu Decision Tree & Random Forest","Đánh giá model với cross-validation"]',
         '["Biết Python cơ bản","Hiểu thống kê mô tả cơ bản (mean, std)"]'),
        (18, 6, 4, 'RAG với LangChain và OpenAI', 'rag-langchain-openai', 'ADVANCED',
         'Thiết kế pipeline tài liệu, embedding, retrieval và chat bot dựa trên RAG.',
         '["Hiểu kiến trúc RAG (Retrieval-Augmented Generation)","Embedding & Vector DB (Chroma/Pinecone)","Xây dựng pipeline LangChain","Tối ưu prompt template & chunking"]',
         '["Biết Python","Hiểu cơ bản về NLP/LLM","Đã dùng qua OpenAI API"]'),
        (19, 7, 4, 'Data Pipeline với Apache Airflow', 'data-pipeline-apache-airflow', 'INTERMEDIATE',
         'Orchestration, DAG, scheduler, retry, monitoring và ETL pipeline với Airflow.',
         '["Viết DAG Airflow đầu tiên","Cài đặt task dependency & branching","Xử lý retry & alerting","Tích hợp sensors & hooks"]',
         '["Biết Python","Hiểu ETL cơ bản","Đã làm việc với SQL"]'),
        (20, 7, 4, 'Kafka cho Hệ Thống Realtime', 'kafka-he-thong-realtime', 'ADVANCED',
         'Topic, partition, consumer group, schema registry và event-driven architecture.',
         '["Thiết kế topic & partition strategy","Cấu hình producer/consumer","Xử lý offset commit & rebalance","Xây dựng event-driven microservices"]',
         '["Hiểu message queue cơ bản (RabbitMQ)","Biết Java hoặc Python","Hiểu về distributed system"]'),
        (21, 8, 4, 'Automation Testing với Selenium', 'automation-testing-selenium', 'BEGINNER',
         'Viết test UI, page object model, wait strategy và report kết quả test.',
         '["Viết test automation Web với Selenium WebDriver","Áp dụng Page Object Model","Hạn chế wait: implicit vs explicit","Generate report HTML/Allure"]',
         '["Biết Java hoặc Python","Hiểu về QA/thủ công testing"]'),
        (22, 8, 4, 'API Testing với Postman và Newman', 'api-testing-postman-newman', 'BEGINNER',
         'Thiết kế collection, environment, assertion, mock server và CI test API.',
         '["Thiết kế Postman collection chuẩn","Dùng biến environment & globals","Viết test script (Pre-request, Tests)","Mock server & documentation"]',
         '["Hiểu về REST API","Đã từng test API thủ công"]'),
        (24, 10, 4, 'Bảo Mật Ứng Dụng Web', 'bao-mat-ung-dung-web', 'INTERMEDIATE',
         'OWASP Top 10, authentication, authorization, secure coding và security testing.',
         '["Nhận diện và phòng chống OWASP Top 10","Implement authentication an toàn","Authorization: RBAC vs ABAC","Secure coding & security testing (SAST, DAST)"]',
         '["Biết về web app development","Đã từng viết API"]')
)
INSERT INTO courses (id, instructor_id, category_id, title, slug, description, level,
                     learning_outcomes, requirements, status, chat_enabled, published_at, created_at, updated_at)
SELECT
    ('20000000-0000-0000-0000-' || lpad(idx::text, 12, '0'))::uuid,
        ('00000000-0000-0000-0000-' || lpad(inst_no::text, 12, '0'))::uuid,
        ('10000000-0000-0000-0000-' || lpad(cat_no::text, 12, '0'))::uuid,
        title, slug, descr, level,
    outcomes::text, prereqs::text,
        'DRAFT', false,
    NULL,
        now() - ((50 - idx) || ' days')::interval,
        now()
FROM course_seed;

-- ============================================================
-- CHAPTERS: 4 chương/khóa (96 chương)
-- ============================================================
INSERT INTO chapters (id, course_id, title, description, order_index, created_at)
SELECT
    ('30000000-0000-0000-0000-' || lpad((row_number() OVER (ORDER BY c.id, ch_no))::text, 12, '0'))::uuid,
        c.id,
    CASE c.category_id
        WHEN '10000000-0000-0000-0000-000000000001' THEN
            CASE ch_no
                WHEN 1 THEN 'Tổng quan & Cài đặt môi trường'
                WHEN 2 THEN 'Xây dựng REST API & Database'
                WHEN 3 THEN 'Bảo mật, Kiểm thử & Xử lý lỗi'
                ELSE 'Deploy, CI/CD & Tối ưu hiệu năng'
                END
        WHEN '10000000-0000-0000-0000-000000000002' THEN
            CASE ch_no
                WHEN 1 THEN 'Khởi tạo dự án & Component cơ bản'
                WHEN 2 THEN 'Quản lý State, Form & Routing'
                WHEN 3 THEN 'Call API, Authentication & Middleware'
                ELSE 'Tối ưu hiệu năng & Deploy'
                END
        WHEN '10000000-0000-0000-0000-000000000003' THEN
            CASE ch_no
                WHEN 1 THEN 'Thiết kế CSDL & Kiểu dữ liệu'
                WHEN 2 THEN 'Index, Execution Plan & Tối ưu Query'
                WHEN 3 THEN 'Transaction, Lock & Replication'
                ELSE 'Partition, Backup & High Availability'
                END
        WHEN '10000000-0000-0000-0000-000000000004' THEN
            CASE ch_no
                WHEN 1 THEN 'Provisioning & Container cơ bản'
                WHEN 2 THEN 'Orchestration & Cấu hình hệ thống'
                WHEN 3 THEN 'CI/CD Pipeline & GitOps'
                ELSE 'Monitor, Logging & Cost Optimization'
                END
        WHEN '10000000-0000-0000-0000-000000000005' THEN
            CASE ch_no
                WHEN 1 THEN 'Thiết kế màn hình & Widget'
                WHEN 2 THEN 'Điều hướng, State & API'
                WHEN 3 THEN 'Thiết bị phần cứng & Native module'
                ELSE 'Build, Test & Publish'
                END
        WHEN '10000000-0000-0000-0000-000000000006' THEN
            CASE ch_no
                WHEN 1 THEN 'Thu thập & Tiền xử lý dữ liệu'
                WHEN 2 THEN 'Xây dựng mô hình & Huấn luyện'
                WHEN 3 THEN 'Đánh giá mô hình & Tuning'
                ELSE 'Triển khai & Monitoring mô hình'
                END
        WHEN '10000000-0000-0000-0000-000000000007' THEN
            CASE ch_no
                WHEN 1 THEN 'Kiến trúc dữ liệu & Pipeline'
                WHEN 2 THEN 'Streaming & Batch Processing'
                WHEN 3 THEN 'Data Quality & Governance'
                ELSE 'Orchestration & Monitoring Pipeline'
                END
        WHEN '10000000-0000-0000-0000-000000000008' THEN
            CASE ch_no
                WHEN 1 THEN 'Test Planning & Test Case Thiết kế'
                WHEN 2 THEN 'Automation Script & Framework'
                WHEN 3 THEN 'Tích hợp CI/CD & Test Report'
                ELSE 'Performance Testing & Security Testing'
                END
        WHEN '10000000-0000-0000-0000-000000000009' THEN
            CASE ch_no
                WHEN 1 THEN 'Yêu cầu & Kiến trúc tổng quan'
                WHEN 2 THEN 'Database & Cache Strategy'
                WHEN 3 THEN 'Microservices & Message Queue'
                ELSE 'Observability & Scalability'
                END
        ELSE
            CASE ch_no
                WHEN 1 THEN 'Fundamentals & Threat Modeling'
                WHEN 2 THEN 'OWASP Top 10 & Vulnerability'
                WHEN 3 THEN 'Authentication, Authorization & Session'
                ELSE 'Secure Coding & Security Testing'
                END
        END,
    CASE ch_no
        WHEN 1 THEN 'Tìm hiểu tổng quan, cài đặt môi trường và tạo dự án mẫu.'
        WHEN 2 THEN 'Thực hành các kỹ năng lập trình cốt lõi của khóa học.'
        WHEN 3 THEN 'Áp dụng kiến thức vào dự án thực tế với bài tập chuyên sâu.'
        ELSE 'Tối ưu sản phẩm, kiểm thử và triển khai lên môi trường production.'
        END,
    ch_no,
    now() - ((30 - ch_no) || ' days')::interval
FROM courses c
         CROSS JOIN generate_series(1, 4) AS ch_no;

-- ============================================================
-- LESSONS: 3 bài/chương (288 bài)
-- Bài 1: VIDEO - preview
-- Bài 2-3: VIDEO - full
-- ============================================================
INSERT INTO lessons (id, chapter_id, course_id, title, order_index, type, duration_seconds, is_preview, video_status, created_at, updated_at)
SELECT
    ('40000000-0000-0000-0000-' || lpad((row_number() OVER (ORDER BY ch.id, lsn_no))::text, 12, '0'))::uuid,
    ch.id, ch.course_id,
    CASE lsn_no
        WHEN 1 THEN 'Giới thiệu: ' || ch.title
        WHEN 2 THEN 'Thực hành: ' || ch.title || ' - Phần 1'
        ELSE 'Thực hành: ' || ch.title || ' - Phần 2'
    END,
    lsn_no,
    'VIDEO',
    450 + lsn_no * 270,
    ch.order_index = 1 AND lsn_no = 1,
    'READY',
    now() - ((20 - lsn_no) || ' days')::interval,
    now()
FROM chapters ch
CROSS JOIN generate_series(1, 3) AS lsn_no;

-- ============================================================
-- SUMMARY
-- Users: 4 (1 admin + 3 instructors)
-- Categories: 10
-- Courses: 24 (DRAFT)
-- Chapters: 96
-- Lessons: 288
-- ============================================================