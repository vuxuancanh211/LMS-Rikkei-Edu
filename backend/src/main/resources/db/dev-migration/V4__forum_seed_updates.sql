UPDATE forum_posts
SET topic = 'ANNOUNCEMENT'
WHERE id = '80000000-0000-0000-0000-000000000001';

UPDATE forum_posts
SET topic = 'QUESTION', reply_count = 3
WHERE id = '80000000-0000-0000-0000-000000000002';

WITH student_reply AS (
    INSERT INTO forum_replies (id, post_id, course_id, author_id, parent_reply_id, content, created_at, updated_at)
    VALUES (
        '81000000-0000-0000-0000-000000000002',
        '80000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000004',
        (
            SELECT id
            FROM forum_replies
            WHERE post_id = '80000000-0000-0000-0000-000000000002'
              AND author_id = '00000000-0000-0000-0000-000000000002'
            ORDER BY created_at
            LIMIT 1
        ),
        'Em cảm ơn thầy, vậy checked exception thì phải cấu hình rollbackFor đúng không ạ?',
        now() - interval '1 day',
        now() - interval '1 day'
    )
    RETURNING id
)
INSERT INTO forum_replies (id, post_id, course_id, author_id, parent_reply_id, content, created_at, updated_at)
SELECT
    '81000000-0000-0000-0000-000000000003',
    '80000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000002',
    id,
    'Đúng rồi em. Đây là tầng trả lời cuối cùng để tránh thảo luận bị lồng quá sâu.',
    now() - interval '20 hours',
    now() - interval '20 hours'
FROM student_reply;
