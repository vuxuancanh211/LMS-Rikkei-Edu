package project.lms_rikkei_edu.modules.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_message_reactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id", "emoji"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessageEntity message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "emoji", length = 10, nullable = false)
    private String emoji;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}