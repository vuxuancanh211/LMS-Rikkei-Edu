-- ============================================================
-- QUIZ MODULE — SEED EXPANSION (Dev/Testing Local)
-- Bổ sung: bank câu hỏi + quiz đủ 3 loại (STATIC / SHUFFLED_POOL / RANDOM_DRAW)
-- cho các khóa học 2, 3, 4 · fix 2 quiz cũ thiếu quiz_type sau khi schema đổi (V9)
-- ============================================================

-- ============================================================
-- 0. FIX: 2 quiz cũ (V2 seed) tạo trước khi V9 đổi cấu trúc random config
--    → quiz_type đang NULL sau migrate, cần gán lại STATIC (đúng bản chất:
--    câu hỏi được chọn cố định từ bank, snapshot vào quiz_questions)
-- ============================================================
UPDATE quizzes SET quiz_type = 'STATIC'
WHERE id IN ('60000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000002')
  AND quiz_type IS NULL;

-- ============================================================
-- 1. BANK QUESTIONS — Course 2: PostgreSQL Nâng Cao
--    2 Easy + 2 Medium + 2 Hard (đủ cho random draw BY_DIFFICULTY 2/2/2)
-- ============================================================
INSERT INTO bank_questions (id, course_id, created_by, subject_tag, question_text, question_type, difficulty, status, created_at) VALUES
    ('61000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
     'Index', 'Index mặc định của PostgreSQL sử dụng cấu trúc dữ liệu nào?', 'SINGLE_CHOICE', 'EASY', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
     'Index', 'Lệnh nào dùng để xem execution plan của một câu truy vấn?', 'SINGLE_CHOICE', 'EASY', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000012', '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
     'Index', 'Khi nào PostgreSQL có thể bỏ qua Index dù đã tạo?', 'MULTIPLE_CHOICE', 'MEDIUM', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000013', '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
     'Partitioning', 'Partitioning theo Range phù hợp nhất với loại dữ liệu nào?', 'SINGLE_CHOICE', 'MEDIUM', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000014', '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
     'Replication', 'Streaming Replication khác Logical Replication ở điểm nào?', 'SINGLE_CHOICE', 'HARD', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000015', '20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
     'Index', 'Các phát biểu nào đúng về Covering Index?', 'MULTIPLE_CHOICE', 'HARD', 'ACTIVE', now());

INSERT INTO bank_options (id, bank_question_id, option_text, is_correct, order_index) VALUES
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000010', 'B-Tree', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000010', 'Hash', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000010', 'GIN', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000010', 'GiST', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000011', 'EXPLAIN ANALYZE', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000011', 'DESCRIBE', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000011', 'SHOW PLAN', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000011', 'ANALYZE TABLE', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000012', 'Khi bảng quá nhỏ, seq scan rẻ hơn', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000012', 'Khi câu truy vấn trả về phần lớn số dòng của bảng', true, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000012', 'Khi cột có index luôn được ưu tiên tuyệt đối', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000012', 'Khi thống kê (statistics) đã lỗi thời', true, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000013', 'Dữ liệu theo thời gian (time-series)', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000013', 'Dữ liệu ngẫu nhiên không thứ tự', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000013', 'Dữ liệu dạng key-value nhỏ', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000013', 'Dữ liệu nhị phân lớn (BLOB)', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000014', 'Logical Replication cho phép replicate một phần bảng/schema', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000014', 'Streaming Replication chỉ replicate toàn bộ cluster ở mức WAL', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000014', 'Cả hai đều không hỗ trợ failover', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000014', 'Không có sự khác biệt', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000015', 'Covering Index chứa đủ cột để trả kết quả mà không cần đọc bảng gốc (Index-Only Scan)', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000015', 'Dùng INCLUDE để thêm cột không dùng để tìm kiếm vào index', true, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000015', 'Covering Index luôn nhỏ hơn Index thông thường', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000015', 'Chỉ áp dụng được cho Hash Index', false, 4);

-- ============================================================
-- 2. BANK QUESTIONS — Course 3: ReactJS & TailwindCSS
--    6 câu mixed difficulty
-- ============================================================
INSERT INTO bank_questions (id, course_id, created_by, subject_tag, question_text, question_type, difficulty, status, created_at) VALUES
    ('61000000-0000-0000-0000-000000000020', '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003',
     'JSX', 'JSX cuối cùng được biên dịch thành lời gọi hàm nào?', 'SINGLE_CHOICE', 'EASY', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000021', '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003',
     'Hooks', 'useState trả về giá trị gì?', 'SINGLE_CHOICE', 'EASY', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000022', '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003',
     'Hooks', 'useEffect với dependency array rỗng [] chạy khi nào?', 'SINGLE_CHOICE', 'MEDIUM', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000023', '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003',
     'State', 'Các cách nào sau đây có thể gây re-render không cần thiết?', 'MULTIPLE_CHOICE', 'MEDIUM', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000024', '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003',
     'TailwindCSS', 'Class nào của Tailwind dùng để căn giữa cả 2 trục trong flexbox?', 'SINGLE_CHOICE', 'EASY', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000025', '20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003',
     'Context', 'Context API phù hợp nhất khi nào?', 'SINGLE_CHOICE', 'HARD', 'ACTIVE', now());

INSERT INTO bank_options (id, bank_question_id, option_text, is_correct, order_index) VALUES
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000020', 'React.createElement()', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000020', 'document.createElement()', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000020', 'ReactDOM.render()', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000020', 'React.compile()', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000021', 'Một mảng [value, setValue]', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000021', 'Một object { value, setValue }', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000021', 'Chỉ giá trị hiện tại', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000021', 'Một Promise', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000022', 'Chỉ 1 lần sau khi component mount', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000022', 'Mỗi lần component re-render', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000022', 'Không bao giờ chạy', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000022', 'Chỉ khi unmount', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000023', 'Tạo object/array literal mới trong mỗi lần render truyền làm props', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000023', 'Định nghĩa function con inline không dùng useCallback', true, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000023', 'Dùng React.memo cho component con', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000023', 'Context value thay đổi object reference mỗi lần render', true, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000024', 'flex items-center justify-center', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000024', 'flex text-center', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000024', 'grid place-self-center', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000024', 'block mx-auto', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000025', 'Chia sẻ state toàn cục cho nhiều component ở nhiều cấp mà không muốn prop drilling', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000025', 'Thay thế hoàn toàn cho mọi state cục bộ của component', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000025', 'Tối ưu hiệu năng re-render tốt hơn useState', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000025', 'Chỉ dùng được với TypeScript', false, 4);

-- ============================================================
-- 3. BANK QUESTIONS — Course 4: Docker & Kubernetes Cơ Bản
--    6 câu ACTIVE (đủ cho random draw FULLY_RANDOM totalCount=4)
-- ============================================================
INSERT INTO bank_questions (id, course_id, created_by, subject_tag, question_text, question_type, difficulty, status, created_at) VALUES
    ('61000000-0000-0000-0000-000000000030', '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003',
     'Docker', 'Lệnh nào dùng để build image từ Dockerfile?', 'SINGLE_CHOICE', 'EASY', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000031', '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003',
     'Docker', 'Sự khác biệt giữa CMD và ENTRYPOINT trong Dockerfile?', 'SINGLE_CHOICE', 'MEDIUM', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000032', '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003',
     'Docker Compose', 'Docker Compose dùng để làm gì?', 'SINGLE_CHOICE', 'EASY', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000033', '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003',
     'Kubernetes', 'Đơn vị triển khai nhỏ nhất trong Kubernetes là gì?', 'SINGLE_CHOICE', 'MEDIUM', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000034', '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003',
     'Kubernetes', 'Service loại nào expose ứng dụng ra ngoài cluster qua LoadBalancer?', 'SINGLE_CHOICE', 'HARD', 'ACTIVE', now()),
    ('61000000-0000-0000-0000-000000000035', '20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003',
     'Docker', 'Các phát biểu nào đúng về Docker volume?', 'MULTIPLE_CHOICE', 'HARD', 'ACTIVE', now());

INSERT INTO bank_options (id, bank_question_id, option_text, is_correct, order_index) VALUES
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000030', 'docker build', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000030', 'docker run', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000030', 'docker compose up', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000030', 'docker create', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000031', 'ENTRYPOINT khó bị override hơn, CMD dễ bị ghi đè khi chạy container', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000031', 'CMD và ENTRYPOINT hoàn toàn giống nhau', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000031', 'ENTRYPOINT chỉ dùng cho image Linux', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000031', 'CMD bắt buộc phải có, ENTRYPOINT thì không', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000032', 'Định nghĩa và chạy nhiều container liên kết nhau bằng 1 file YAML', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000032', 'Thay thế hoàn toàn Dockerfile', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000032', 'Chỉ dùng để build image', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000032', 'Quản lý cluster Kubernetes', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000033', 'Pod', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000033', 'Container', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000033', 'Node', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000033', 'Deployment', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000034', 'LoadBalancer', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000034', 'ClusterIP', false, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000034', 'NodePort', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000034', 'Headless', false, 4),

    (gen_random_uuid(), '61000000-0000-0000-0000-000000000035', 'Volume giúp dữ liệu tồn tại độc lập với vòng đời container', true, 1),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000035', 'Bind mount là một dạng đưa thư mục host vào container', true, 2),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000035', 'Volume luôn bị xóa khi container bị xóa', false, 3),
    (gen_random_uuid(), '61000000-0000-0000-0000-000000000035', 'Named volume được Docker quản lý, không cần biết đường dẫn host', true, 4);

-- ============================================================
-- 4. QUIZ — Course 2 (PostgreSQL): RANDOM_DRAW / BY_DIFFICULTY
--    Rút 2 Easy + 2 Medium + 2 Hard mỗi lần học viên bắt đầu làm bài
-- ============================================================
INSERT INTO quizzes (id, course_id, created_by, title, description, quiz_type, duration_minutes, max_attempts,
                      pass_score, shuffle_questions, shuffle_options, proctoring_enabled, status,
                      random_mode, difficulty_config, cooldown_minutes, published_at, created_at, updated_at) VALUES
    ('60000000-0000-0000-0000-000000000003',
     '20000000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000002',
     'Quiz: Tổng hợp PostgreSQL Nâng Cao', 'Rút ngẫu nhiên theo tỉ lệ độ khó từ toàn bộ ngân hàng câu hỏi',
     'RANDOM_DRAW', 25, 3, 60.00, true, true, false, 'PUBLISHED',
     'BY_DIFFICULTY', '{"EASY":2,"MEDIUM":2,"HARD":2}'::jsonb, 20,
     now() - interval '5 days', now() - interval '6 days', now());

-- ============================================================
-- 5. QUIZ — Course 3 (ReactJS): SHUFFLED_POOL
--    4 câu cố định được instructor chọn sẵn, xáo thứ tự mỗi lần thi
-- ============================================================
INSERT INTO quizzes (id, course_id, created_by, title, description, quiz_type, duration_minutes, max_attempts,
                      pass_score, shuffle_questions, shuffle_options, proctoring_enabled, status,
                      cooldown_minutes, published_at, created_at, updated_at) VALUES
    ('60000000-0000-0000-0000-000000000004',
     '20000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000003',
     'Quiz: React Hooks & JSX Cơ Bản', 'Bộ câu hỏi cố định, xáo trộn thứ tự mỗi lần làm bài',
     'SHUFFLED_POOL', 15, 3, 60.00, true, true, false, 'PUBLISHED',
     20, now() - interval '4 days', now() - interval '5 days', now());

INSERT INTO quiz_questions (id, quiz_id, bank_question_id, question_text, question_type, difficulty, subject_tag, order_index) VALUES
    ('62000000-0000-0000-0000-000000000004', '60000000-0000-0000-0000-000000000004', '61000000-0000-0000-0000-000000000020',
     'JSX cuối cùng được biên dịch thành lời gọi hàm nào?', 'SINGLE_CHOICE', 'EASY', 'JSX', 1),
    ('62000000-0000-0000-0000-000000000005', '60000000-0000-0000-0000-000000000004', '61000000-0000-0000-0000-000000000021',
     'useState trả về giá trị gì?', 'SINGLE_CHOICE', 'EASY', 'Hooks', 2),
    ('62000000-0000-0000-0000-000000000006', '60000000-0000-0000-0000-000000000004', '61000000-0000-0000-0000-000000000022',
     'useEffect với dependency array rỗng [] chạy khi nào?', 'SINGLE_CHOICE', 'MEDIUM', 'Hooks', 3),
    ('62000000-0000-0000-0000-000000000007', '60000000-0000-0000-0000-000000000004', '61000000-0000-0000-0000-000000000023',
     'Các cách nào sau đây có thể gây re-render không cần thiết?', 'MULTIPLE_CHOICE', 'MEDIUM', 'State', 4);

INSERT INTO quiz_options (id, question_id, option_text, is_correct, order_index) VALUES
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000004', 'React.createElement()', true, 1),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000004', 'document.createElement()', false, 2),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000004', 'ReactDOM.render()', false, 3),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000004', 'React.compile()', false, 4),

    (gen_random_uuid(), '62000000-0000-0000-0000-000000000005', 'Một mảng [value, setValue]', true, 1),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000005', 'Một object { value, setValue }', false, 2),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000005', 'Chỉ giá trị hiện tại', false, 3),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000005', 'Một Promise', false, 4),

    (gen_random_uuid(), '62000000-0000-0000-0000-000000000006', 'Chỉ 1 lần sau khi component mount', true, 1),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000006', 'Mỗi lần component re-render', false, 2),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000006', 'Không bao giờ chạy', false, 3),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000006', 'Chỉ khi unmount', false, 4),

    (gen_random_uuid(), '62000000-0000-0000-0000-000000000007', 'Tạo object/array literal mới trong mỗi lần render truyền làm props', true, 1),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000007', 'Định nghĩa function con inline không dùng useCallback', true, 2),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000007', 'Dùng React.memo cho component con', false, 3),
    (gen_random_uuid(), '62000000-0000-0000-0000-000000000007', 'Context value thay đổi object reference mỗi lần render', true, 4);

-- ============================================================
-- 6. QUIZ — Course 4 (Docker): RANDOM_DRAW / FULLY_RANDOM
--    Rút ngẫu nhiên 4 câu bất kỳ trong ngân hàng, không phân biệt độ khó
-- ============================================================
INSERT INTO quizzes (id, course_id, created_by, title, description, quiz_type, duration_minutes, max_attempts,
                      pass_score, shuffle_questions, shuffle_options, proctoring_enabled, status,
                      random_mode, random_total_count, cooldown_minutes, published_at, created_at, updated_at) VALUES
    ('60000000-0000-0000-0000-000000000005',
     '20000000-0000-0000-0000-000000000004',
     '00000000-0000-0000-0000-000000000003',
     'Quiz: Docker & Kubernetes Tổng Hợp', 'Rút ngẫu nhiên hoàn toàn 4 câu mỗi lần làm bài',
     'RANDOM_DRAW', 15, 3, 50.00, true, true, false, 'PUBLISHED',
     'FULLY_RANDOM', 4, 20, now() - interval '3 days', now() - interval '4 days', now());

-- ============================================================
-- 7. QUIZ — Course 1 (Spring Boot): DRAFT rỗng, chưa có câu hỏi
--    Dùng để test luồng "tạo quiz → thêm câu hỏi" ngay trên UI (QuizDetailModal)
-- ============================================================
INSERT INTO quizzes (id, course_id, created_by, title, description, quiz_type, duration_minutes, max_attempts,
                      pass_score, shuffle_questions, shuffle_options, proctoring_enabled, status,
                      cooldown_minutes, created_at, updated_at) VALUES
    ('60000000-0000-0000-0000-000000000006',
     '20000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000002',
     'Quiz: Kiểm tra cuối khoá (đang soạn)', 'Chưa thêm câu hỏi — dùng để test luồng soạn quiz',
     'STATIC', 30, 3, 60.00, false, false, false, 'DRAFT',
     20, now() - interval '1 day', now());

-- ============================================================
-- SUMMARY (V11)
-- ============================================================
-- Fix         : 2 quiz cũ (course 1) được gán lại quiz_type = STATIC
-- Bank mới    : 18 câu hỏi (6 course 2, 6 course 3, 6 course 4)
-- Quiz mới    : 4 quiz
--   - Course 2: RANDOM_DRAW / BY_DIFFICULTY (2 Easy + 2 Medium + 2 Hard), PUBLISHED
--   - Course 3: SHUFFLED_POOL (4 câu snapshot cố định), PUBLISHED
--   - Course 4: RANDOM_DRAW / FULLY_RANDOM (4 câu), PUBLISHED
--   - Course 1: STATIC, DRAFT, rỗng — để test thêm câu hỏi qua UI
-- ============================================================
