package project.lms_rikkei_edu.modules.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Column(name = "notification_type", nullable = false, length = 40)
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    @Column(name = "is_read", nullable = false)
    private Boolean read;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "email_sent", nullable = false)
    private Boolean emailSent;

    @Column(name = "email_sent_at")
    private OffsetDateTime emailSentAt;

    @Column(name = "push_sent", nullable = false)
    private Boolean pushSent;

    @Column(name = "push_sent_at")
    private OffsetDateTime pushSentAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
