package project.lms_rikkei_edu.modules.assignment.entity;

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
@Table(name = "assignment_attachments")
public class AssignmentAttachmentEntity {

    @Id
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;
}
