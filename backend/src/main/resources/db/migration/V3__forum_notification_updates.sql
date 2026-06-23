ALTER TABLE forum_posts
    ADD COLUMN topic varchar(30),
    ADD COLUMN upvote_count int DEFAULT 0;

ALTER TABLE forum_replies
    ADD COLUMN parent_reply_id uuid,
    ADD COLUMN upvote_count int DEFAULT 0;

CREATE TABLE forum_reactions (
                                 id uuid PRIMARY KEY,
                                 post_id uuid,
                                 reply_id uuid,
                                 user_id uuid NOT NULL,
                                 created_at timestamptz DEFAULT (now()),
                                 CONSTRAINT chk_forum_reaction_target CHECK (
                                     (post_id IS NOT NULL AND reply_id IS NULL) OR
                                     (post_id IS NULL AND reply_id IS NOT NULL)
                                 )
);

ALTER TABLE notifications RENAME COLUMN "type" TO notification_type;

ALTER TABLE forum_replies
    ADD CONSTRAINT fk_forum_replies_parent_reply
        FOREIGN KEY (parent_reply_id) REFERENCES forum_replies (id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE forum_reactions
    ADD CONSTRAINT fk_forum_reactions_post
        FOREIGN KEY (post_id) REFERENCES forum_posts (id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE forum_reactions
    ADD CONSTRAINT fk_forum_reactions_reply
        FOREIGN KEY (reply_id) REFERENCES forum_replies (id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE forum_reactions
    ADD CONSTRAINT fk_forum_reactions_user
        FOREIGN KEY (user_id) REFERENCES users (id) DEFERRABLE INITIALLY IMMEDIATE;

CREATE UNIQUE INDEX ux_forum_reactions_post_user
    ON forum_reactions (post_id, user_id)
    WHERE post_id IS NOT NULL;

CREATE UNIQUE INDEX ux_forum_reactions_reply_user
    ON forum_reactions (reply_id, user_id)
    WHERE reply_id IS NOT NULL;
