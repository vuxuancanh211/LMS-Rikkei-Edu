package project.lms_rikkei_edu.modules.notification.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class NotificationPreferenceResponse {
    UUID id;
    String type;
    boolean inAppEnabled;
    boolean emailEnabled;
    boolean pushEnabled;
}
