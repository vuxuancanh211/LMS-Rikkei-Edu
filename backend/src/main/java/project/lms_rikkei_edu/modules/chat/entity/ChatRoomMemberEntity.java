package project.lms_rikkei_edu.modules.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_room_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private ChatMessageEntity lastReadMessage;

    @Column(name = "is_muted")
    private boolean muted = false;

    @Column(name = "muted_until")
    private OffsetDateTime mutedUntil;

    @PrePersist
    void prePersist() {
        joinedAt = OffsetDateTime.now();
    }

    public enum MemberRole {
        MEMBER, MODERATOR
    }
}
