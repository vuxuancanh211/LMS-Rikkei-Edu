package project.lms_rikkei_edu.modules.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Represents one text chunk from an ingested knowledge source.
 *
 * NOTE: The {@code embedding} column (vector(1024)) is intentionally NOT mapped
 * as a JPA field — Hibernate has no built-in mapping for pgvector.
 * All embedding reads/writes go through {@code VectorSearchService} which uses
 * JdbcTemplate and PostgreSQL's {@code ::vector} cast operator.
 */
@Entity
@Table(name = "document_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "section_title", length = 255)
    private String sectionTitle;

    @Column(name = "chunk_text", columnDefinition = "text")
    private String chunkText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
