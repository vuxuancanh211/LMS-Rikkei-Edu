package project.lms_rikkei_edu.modules.notification.dto.response;

import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
public class NotificationListItemResponse {
    UUID id;
    String type;
    String title;
    String body;
    String referenceType;
    UUID referenceId;
    boolean read;
    OffsetDateTime createdAt;
}
