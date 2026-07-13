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
@Table(name = "submission_files")
public class SubmissionFileEntity {

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "s3_key", length = 500, nullable = false)
    private String s3Key;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(length = 20)
    private String extension;

    @Column(name = "scan_status", length = 20)
    private String scanStatus;

    @Column(name = "scan_threat_name", length = 255)
    private String scanThreatName;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;
}
