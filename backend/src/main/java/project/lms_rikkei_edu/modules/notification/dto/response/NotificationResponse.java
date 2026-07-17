package project.lms_rikkei_edu.modules.notification.dto.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
@Jacksonized
public class NotificationResponse {
    UUID id;
    UUID recipientId;
    String type;
    String title;
    String body;
    String referenceType;
    UUID referenceId;
    UUID actorId;
    String actorName;
    String priority;
    boolean read;
    OffsetDateTime createdAt;
}
