DELETE FROM chat_message_reactions
WHERE message_id IN (
    SELECT id FROM chat_messages
    WHERE room_id IN (
        SELECT id FROM chat_rooms WHERE room_type != 'GROUP'
    )
    );

DELETE FROM chat_messages
WHERE room_id IN (
    SELECT id FROM chat_rooms WHERE room_type != 'GROUP'
    );

DELETE FROM chat_room_members
WHERE room_id IN (
    SELECT id FROM chat_rooms WHERE room_type != 'GROUP'
    );

DELETE FROM chat_rooms WHERE room_type != 'GROUP';

ALTER TABLE chat_rooms DROP COLUMN IF EXISTS room_type;

ALTER TABLE chat_rooms DROP COLUMN IF EXISTS course_id;

ALTER TABLE chat_rooms ALTER COLUMN group_id SET NOT NULL;

ALTER TABLE chat_rooms
    ADD CONSTRAINT uq_chat_rooms_group UNIQUE (group_id);

DROP INDEX IF EXISTS chat_message_reactions_message_id_user_id_emoji_idx;