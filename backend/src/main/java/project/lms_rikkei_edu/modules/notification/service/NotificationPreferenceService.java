package project.lms_rikkei_edu.modules.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.notification.dto.request.UpdateNotificationPreferenceRequest;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationPreferenceResponse;
import project.lms_rikkei_edu.modules.notification.entity.NotificationPreferenceEntity;
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.repository.NotificationPreferenceRepository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(UUID userId) {
        List<NotificationPreferenceEntity> existing = preferenceRepository.findByUserId(userId);
        Map<String, NotificationPreferenceEntity> byType = existing.stream()
                .collect(Collectors.toMap(NotificationPreferenceEntity::getType, Function.identity()));

        return Arrays.stream(NotificationType.values())
                .map(type -> {
                    NotificationPreferenceEntity pref = byType.get(type.name());
                    if (pref == null) {
                        return NotificationPreferenceResponse.builder()
                                .id(null)
                                .type(type.name())
                                .inAppEnabled(true)
                                .emailEnabled(true)
                                .pushEnabled(true)
                                .build();
                    }
                    return toResponse(pref);
                })
                .toList();
    }

    @Transactional
    public NotificationPreferenceResponse updatePreference(UUID userId, String type, UpdateNotificationPreferenceRequest request) {
        NotificationPreferenceEntity pref = preferenceRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> {
                    NotificationPreferenceEntity newPref = new NotificationPreferenceEntity();
                    newPref.setId(UUID.randomUUID());
                    newPref.setUserId(userId);
                    newPref.setType(type);
                    return newPref;
                });

        pref.setInAppEnabled(request.isInAppEnabled());
        pref.setEmailEnabled(request.isEmailEnabled());
        pref.setPushEnabled(request.isPushEnabled());
        pref.setUpdatedAt(OffsetDateTime.now());

        NotificationPreferenceEntity saved = preferenceRepository.save(pref);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public boolean isInAppEnabled(UUID userId, String type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .map(NotificationPreferenceEntity::getInAppEnabled)
                .orElse(true);
    }

    private NotificationPreferenceResponse toResponse(NotificationPreferenceEntity entity) {
        return NotificationPreferenceResponse.builder()
                .id(entity.getId())
                .type(entity.getType())
                .inAppEnabled(Boolean.TRUE.equals(entity.getInAppEnabled()))
                .emailEnabled(Boolean.TRUE.equals(entity.getEmailEnabled()))
                .pushEnabled(Boolean.TRUE.equals(entity.getPushEnabled()))
                .build();
    }
}
