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
@Table(name = "notification_preferences")
public class NotificationPreferenceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "notification_type", nullable = false, length = 40)
    private String type;

    @Column(name = "in_app_enabled")
    private Boolean inAppEnabled;

    @Column(name = "email_enabled")
    private Boolean emailEnabled;

    @Column(name = "push_enabled")
    private Boolean pushEnabled;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
