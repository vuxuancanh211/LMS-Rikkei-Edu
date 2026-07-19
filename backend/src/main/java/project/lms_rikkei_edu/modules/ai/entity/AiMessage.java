package project.lms_rikkei_edu.modules.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import project.lms_rikkei_edu.modules.ai.entity.enums.MessageRole;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "llm_provider", length = 50)
    private String llmProvider;

    @Column(name = "llm_model", length = 100)
    private String llmModel;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
