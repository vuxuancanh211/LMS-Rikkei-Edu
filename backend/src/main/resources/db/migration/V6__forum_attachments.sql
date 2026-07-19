CREATE TABLE forum_attachments (
    id uuid PRIMARY KEY,
    post_id uuid,
    reply_id uuid,
    uploader_id uuid NOT NULL,
    file_name varchar(255) NOT NULL,
    file_key varchar(500) NOT NULL,
    content_type varchar(100) NOT NULL,
    size_bytes bigint NOT NULL,
    attachment_type varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_forum_attachment_target CHECK (
        (post_id IS NOT NULL AND reply_id IS NULL) OR
        (post_id IS NULL AND reply_id IS NOT NULL) OR
        (post_id IS NULL AND reply_id IS NULL)
    )
);

ALTER TABLE forum_attachments
    ADD CONSTRAINT fk_forum_attachments_post
        FOREIGN KEY (post_id) REFERENCES forum_posts (id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE forum_attachments
    ADD CONSTRAINT fk_forum_attachments_reply
        FOREIGN KEY (reply_id) REFERENCES forum_replies (id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE forum_attachments
    ADD CONSTRAINT fk_forum_attachments_uploader
        FOREIGN KEY (uploader_id) REFERENCES users (id) DEFERRABLE INITIALLY IMMEDIATE;

CREATE INDEX idx_forum_attachments_post ON forum_attachments(post_id) WHERE post_id IS NOT NULL;
CREATE INDEX idx_forum_attachments_reply ON forum_attachments(reply_id) WHERE reply_id IS NOT NULL;
CREATE INDEX idx_forum_attachments_uploader_unattached ON forum_attachments(uploader_id) WHERE post_id IS NULL AND reply_id IS NULL;
