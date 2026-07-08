package project.lms_rikkei_edu.modules.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import project.lms_rikkei_edu.modules.ai.entity.enums.ConversationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "title", length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ConversationStatus status;

    @Column(name = "message_count")
    private Integer messageCount;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;
}
