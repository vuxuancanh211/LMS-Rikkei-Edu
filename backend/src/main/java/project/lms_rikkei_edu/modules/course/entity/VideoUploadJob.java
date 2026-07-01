package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import project.lms_rikkei_edu.modules.course.enums.UploadStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_upload_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoUploadJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", length = 30)
    private UploadStatus uploadStatus;

    @Column(name = "transcoding_started_at")
    private Instant transcodingStartedAt;

    @Column(name = "transcoding_completed_at")
    private Instant transcodingCompletedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
