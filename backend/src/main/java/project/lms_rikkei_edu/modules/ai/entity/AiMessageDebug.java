package project.lms_rikkei_edu.modules.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_message_debugs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageDebug {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    /** JSON array of chunk details used in context. Stored as raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retrieved_chunks", columnDefinition = "jsonb")
    private String retrievedChunks;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
