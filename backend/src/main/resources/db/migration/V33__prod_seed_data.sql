-- Minimal production seed for first deployment/demo.
-- Default password for seeded users: 123456
-- Change these passwords immediately after the first login in a real production environment.

INSERT INTO users (id, email, full_name, password_hash, role, status, phone_number, bio, created_at, updated_at)
VALUES
    ('90000000-0000-0000-0000-000000000001', 'admin@rikkei.edu', 'Admin System',
     '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
     'ADMIN', 'ACTIVE', '0990000001', 'System administrator', now(), now()),
    ('90000000-0000-0000-0000-000000000002', 'instructor@rikkei.edu', 'Demo Instructor',
     '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
     'INSTRUCTOR', 'ACTIVE', '0990000002', 'Demo instructor account', now(), now()),
    ('90000000-0000-0000-0000-000000000003', 'student@rikkei.edu', 'Demo Student',
     '$2y$12$NY8m2Y/3xrXw6U1MvaDeWekKwyK.aoN7a61NgQXG11J5Hhc8ikHvC',
     'STUDENT', 'ACTIVE', '0990000003', 'Demo student account', now(), now())
    ON CONFLICT (email) DO NOTHING;

INSERT INTO course_categories (id, name, slug, is_active, created_at)
VALUES
    ('91000000-0000-0000-0000-000000000001', 'Backend Development', 'backend-development', true, now()),
    ('91000000-0000-0000-0000-000000000002', 'Frontend Development', 'frontend-development', true, now()),
    ('91000000-0000-0000-0000-000000000003', 'DevOps & Cloud', 'devops-cloud', true, now())
    ON CONFLICT (slug) DO NOTHING;

INSERT INTO courses (
    id, instructor_id, category_id, title, slug, description, level, status,
    chat_enabled, published_at, created_at, updated_at, learning_outcomes, requirements
)
SELECT
    seeded.id,
    instructor.id,
    category.id,
    seeded.title,
    seeded.slug,
    seeded.description,
    seeded.level,
    'PUBLISHED',
    seeded.chat_enabled,
    now(),
    now(),
    now(),
    seeded.learning_outcomes,
    seeded.requirements
FROM users instructor
         CROSS JOIN (
    VALUES
        ('92000000-0000-0000-0000-000000000001'::uuid, 'backend-development', 'Java Spring Boot Fundamentals', 'java-spring-boot-fundamentals',
         'A starter Backend course for testing the LMS production deployment: Spring Boot, REST API, Security, and Docker basics.',
         'BEGINNER', true,
         'Understand Spring Boot project structure; Build simple REST APIs; Understand authentication basics',
         'Basic Java knowledge; Basic web API knowledge'),
        ('92000000-0000-0000-0000-000000000002'::uuid, 'frontend-development', 'React Frontend Fundamentals', 'react-frontend-fundamentals',
         'A starter Frontend course for testing React pages, routing, components, and API integration in the LMS.',
         'BEGINNER', true,
         'Understand React components; Use routing; Call backend APIs from frontend',
         'Basic HTML, CSS, and JavaScript knowledge'),
        ('92000000-0000-0000-0000-000000000003'::uuid, 'devops-cloud', 'Docker Deployment Basics', 'docker-deployment-basics',
         'A starter DevOps course for testing Docker, Docker Compose, Nginx gateway, and EC2 deployment flow.',
         'BEGINNER', true,
         'Understand Docker images; Run services with Docker Compose; Deploy a basic app to EC2',
         'Basic command line knowledge')
) AS seeded(id, category_slug, title, slug, description, level, chat_enabled, learning_outcomes, requirements)
         JOIN course_categories category ON category.slug = seeded.category_slug
WHERE instructor.email = 'instructor@rikkei.edu'
    ON CONFLICT (slug) DO NOTHING;

INSERT INTO course_approval_logs (id, course_id, admin_id, action, reason, created_at)
SELECT
    gen_random_uuid(),
    course.id,
    admin.id,
    'APPROVED',
    'Initial production seed course.',
    now()
FROM courses course
         CROSS JOIN users admin
WHERE course.slug IN ('java-spring-boot-fundamentals', 'react-frontend-fundamentals', 'docker-deployment-basics')
  AND admin.email = 'admin@rikkei.edu'
    ON CONFLICT DO NOTHING;

INSERT INTO chapters (id, course_id, title, description, order_index, created_at, is_draft, pending_delete, deleted_at)
SELECT
    seeded.id,
    course.id,
    seeded.title,
    seeded.description,
    seeded.order_index,
    now(),
    false,
    false,
    NULL
FROM courses course
         JOIN (
    VALUES
        ('93000000-0000-0000-0000-000000000001'::uuid, 'java-spring-boot-fundamentals', 'Getting Started With Spring Boot', 'Spring Boot setup and first application.', 1),
        ('93000000-0000-0000-0000-000000000002'::uuid, 'react-frontend-fundamentals', 'Getting Started With React', 'React project structure and component basics.', 1),
        ('93000000-0000-0000-0000-000000000003'::uuid, 'docker-deployment-basics', 'Getting Started With Docker', 'Docker images, containers, and compose basics.', 1)
) AS seeded(id, course_slug, title, description, order_index) ON seeded.course_slug = course.slug
    ON CONFLICT (id) DO NOTHING;

INSERT INTO lessons (
    id, chapter_id, course_id, title, order_index, type, content_text,
    duration_seconds, is_preview, video_status, created_at, updated_at,
    is_draft, pending_delete, deleted_at
)
SELECT
    seeded.id,
    chapter.id,
    course.id,
    seeded.title,
    seeded.order_index,
    'TEXT',
    seeded.content_text,
    NULL,
    seeded.is_preview,
    'NONE',
    now(),
    now(),
    false,
    false,
    NULL
FROM chapters chapter
         JOIN courses course ON course.id = chapter.course_id
         JOIN (
    VALUES
        ('94000000-0000-0000-0000-000000000001'::uuid, '93000000-0000-0000-0000-000000000001'::uuid, 'What is Spring Boot?', 1, true,
         'Spring Boot helps developers create standalone, production-ready Spring applications quickly.'),
        ('94000000-0000-0000-0000-000000000002'::uuid, '93000000-0000-0000-0000-000000000002'::uuid, 'What is React?', 1, true,
         'React helps developers build interactive user interfaces from reusable components.'),
        ('94000000-0000-0000-0000-000000000003'::uuid, '93000000-0000-0000-0000-000000000003'::uuid, 'What is Docker?', 1, true,
         'Docker packages applications and dependencies into portable containers for consistent deployment.')
) AS seeded(id, chapter_id, title, order_index, is_preview, content_text) ON seeded.chapter_id = chapter.id
    ON CONFLICT (id) DO NOTHING;

INSERT INTO course_enrollments (id, course_id, student_id, enrolled_by, enrolled_at)
SELECT
    gen_random_uuid(),
    course.id,
    student.id,
    admin.id,
    now()
FROM courses course
         CROSS JOIN users student
         CROSS JOIN users admin
WHERE course.slug IN ('java-spring-boot-fundamentals', 'react-frontend-fundamentals', 'docker-deployment-basics')
  AND student.email = 'student@rikkei.edu'
  AND admin.email = 'admin@rikkei.edu'
    ON CONFLICT DO NOTHING;