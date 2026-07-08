package project.lms_rikkei_edu.modules.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import project.lms_rikkei_edu.modules.ai.entity.enums.JobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_ingestion_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiIngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "job_type", length = 30)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private JobStatus status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
