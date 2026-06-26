package project.lms_rikkei_edu.modules.notification.dto.request;

import lombok.Value;

@Value
public class UpdateNotificationPreferenceRequest {
    boolean inAppEnabled;
    boolean emailEnabled;
    boolean pushEnabled;
}
