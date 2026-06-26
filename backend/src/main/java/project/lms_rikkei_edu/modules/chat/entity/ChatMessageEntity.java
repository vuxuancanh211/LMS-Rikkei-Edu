package project.lms_rikkei_edu.modules.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private UserEntity sender;

    @Enumerated(EnumType.STRING)          // thêm annotation bị thiếu
    @Column(name = "message_type", length = 20)
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "attachment_url", columnDefinition = "text")
    private String attachmentUrl;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_size_bytes")
    private Long attachmentSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private ChatMessageEntity replyTo;

    @Column(name = "is_edited")
    private boolean edited = false;

    @Column(name = "edited_at")
    private OffsetDateTime editedAt;

    @Column(name = "is_deleted")
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessageReactionEntity> reactions = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public enum MessageType {
        TEXT, FILE, SYSTEM
    }
}
