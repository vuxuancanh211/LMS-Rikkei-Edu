DELETE FROM chat_message_reactions
WHERE message_id IN (
    SELECT id FROM chat_messages
    WHERE room_id IN ('90000000-0000-0000-0000-000000000001',
                      '90000000-0000-0000-0000-000000000002')
);

DELETE FROM chat_messages
WHERE room_id IN ('90000000-0000-0000-0000-000000000001',
                  '90000000-0000-0000-0000-000000000002');

DELETE FROM chat_room_members
WHERE room_id IN ('90000000-0000-0000-0000-000000000001',
                  '90000000-0000-0000-0000-000000000002');

DELETE FROM chat_rooms
WHERE id IN ('90000000-0000-0000-0000-000000000001',
             '90000000-0000-0000-0000-000000000002');

INSERT INTO chat_rooms (id, name, group_id, created_by, is_active, last_message_at, created_at)
VALUES
    ('90000000-0000-0000-0000-000000000002',
     'Nhóm Spring Boot - Buổi Tối',
     '50000000-0000-0000-0000-000000000001',
     '00000000-0000-0000-0000-000000000002',
     true, now() - interval '2 hours', now() - interval '19 days'),

    ('90000000-0000-0000-0000-000000000003',
     'Nhóm Spring Boot - Cuối Tuần',
     '50000000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000002',
     true, now() - interval '3 days', now() - interval '18 days'),

    ('90000000-0000-0000-0000-000000000004',
     'Nhóm ReactJS - Beginner',
     '50000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000003',
     true, now() - interval '5 days', now() - interval '12 days');

INSERT INTO chat_room_members (id, room_id, user_id, role, joined_at)
VALUES
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'MODERATOR', now() - interval '19 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004', 'MEMBER',    now() - interval '19 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005', 'MEMBER',    now() - interval '18 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000006', 'MEMBER',    now() - interval '17 days'),

    (gen_random_uuid(), '90000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', 'MODERATOR', now() - interval '18 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000007', 'MEMBER',    now() - interval '15 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000008', 'MEMBER',    now() - interval '14 days'),

    (gen_random_uuid(), '90000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003', 'MODERATOR', now() - interval '12 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000005', 'MEMBER',    now() - interval '10 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000010', 'MEMBER',    now() - interval '9 days');

INSERT INTO chat_messages (id, room_id, sender_id, message_type, content, created_at)
VALUES
    -- Nhóm Spring Boot - Buổi Tối
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000002', 'TEXT',
     'Chào cả nhóm! Tối nay mình ôn lại phần JWT nhé, có quiz vào tuần sau.',
     now() - interval '2 hours'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000004', 'TEXT',
     'Dạ vâng thầy!',
     now() - interval '1 hour'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000005', 'TEXT',
     'Em cũng đang ôn rồi ạ.',
     now() - interval '30 minutes'),

    -- Nhóm Spring Boot - Cuối Tuần
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000002', 'TEXT',
     'Cuối tuần này học từ 9h nhé các bạn.',
     now() - interval '3 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000003',
     '00000000-0000-0000-0000-000000000007', 'TEXT',
     'Dạ em nhớ rồi ạ.',
     now() - interval '3 days' + interval '10 minutes'),

    -- Nhóm ReactJS - Beginner
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000004',
     '00000000-0000-0000-0000-000000000003', 'TEXT',
     'Chào các bạn! Hôm nay mình học về useState và useEffect nhé.',
     now() - interval '5 days'),
    (gen_random_uuid(), '90000000-0000-0000-0000-000000000004',
     '00000000-0000-0000-0000-000000000010', 'TEXT',
     'Thầy ơi, em bị lỗi khi dùng useEffect với async function ạ.',
     now() - interval '5 days' + interval '20 minutes');
