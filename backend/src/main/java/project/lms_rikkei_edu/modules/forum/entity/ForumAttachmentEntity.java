package project.lms_rikkei_edu.modules.forum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "forum_attachments")
public class ForumAttachmentEntity {

    @Id
    private UUID id;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "reply_id")
    private UUID replyId;

    @Column(name = "uploader_id", nullable = false)
    private UUID uploaderId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "attachment_type", nullable = false)
    private String attachmentType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
