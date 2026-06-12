package project.lms_rikkei_edu.modules.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_sources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 30)
    private SourceType sourceType;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "external_id", length = 255)
    private String externalId;

    /** Lifecycle status of the source record itself (ACTIVE / DELETED). */
    @Column(name = "status", length = 20)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingest_status", length = 20)
    private IngestStatus ingestStatus;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "indexed_at")
    private OffsetDateTime indexedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
