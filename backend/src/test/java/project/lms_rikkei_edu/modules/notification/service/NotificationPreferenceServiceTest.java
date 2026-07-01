package project.lms_rikkei_edu.modules.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.modules.notification.dto.request.UpdateNotificationPreferenceRequest;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationPreferenceResponse;
import project.lms_rikkei_edu.modules.notification.entity.NotificationPreferenceEntity;
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.repository.NotificationPreferenceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private NotificationPreferenceService preferenceService;

    @Captor
    private ArgumentCaptor<NotificationPreferenceEntity> entityCaptor;

    private final UUID userId = UUID.randomUUID();

    @Test
    void getPreferencesReturnsDefaultsWhenNoneExist() {
        when(preferenceRepository.findByUserId(userId)).thenReturn(List.of());

        List<NotificationPreferenceResponse> result = preferenceService.getPreferences(userId);

        assertThat(result).hasSize(NotificationType.values().length);
        result.forEach(pref -> {
            assertThat(pref.isInAppEnabled()).isTrue();
            assertThat(pref.isEmailEnabled()).isTrue();
            assertThat(pref.isPushEnabled()).isTrue();
            assertThat(pref.getId()).isNull();
        });
    }

    @Test
    void getPreferencesMergesExistingWithDefaults() {
        NotificationPreferenceEntity existing = new NotificationPreferenceEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setType("FORUM_REPLY");
        existing.setInAppEnabled(false);
        existing.setEmailEnabled(true);
        existing.setPushEnabled(false);

        when(preferenceRepository.findByUserId(userId)).thenReturn(List.of(existing));

        List<NotificationPreferenceResponse> result = preferenceService.getPreferences(userId);

        assertThat(result).hasSize(NotificationType.values().length);

        NotificationPreferenceResponse forumReply = result.stream()
                .filter(p -> "FORUM_REPLY".equals(p.getType()))
                .findFirst().orElseThrow();
        assertThat(forumReply.isInAppEnabled()).isFalse();
        assertThat(forumReply.isEmailEnabled()).isTrue();
        assertThat(forumReply.isPushEnabled()).isFalse();
        assertThat(forumReply.getId()).isEqualTo(existing.getId());

        NotificationPreferenceResponse other = result.stream()
                .filter(p -> !"FORUM_REPLY".equals(p.getType()))
                .findFirst().orElseThrow();
        assertThat(other.isInAppEnabled()).isTrue();
        assertThat(other.getId()).isNull();
    }

    @Test
    void updatePreferenceCreatesNewWhenNoneExists() {
        String type = "QUIZ_PUBLISHED";
        UpdateNotificationPreferenceRequest request = new UpdateNotificationPreferenceRequest(false, true, false);

        when(preferenceRepository.findByUserIdAndType(userId, type)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreferenceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceResponse result = preferenceService.updatePreference(userId, type, request);

        assertThat(result.getType()).isEqualTo(type);
        assertThat(result.isInAppEnabled()).isFalse();
        assertThat(result.isEmailEnabled()).isTrue();
        assertThat(result.isPushEnabled()).isFalse();

        verify(preferenceRepository).save(entityCaptor.capture());
        NotificationPreferenceEntity saved = entityCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(type);
        assertThat(saved.getInAppEnabled()).isFalse();
    }

    @Test
    void updatePreferenceUpdatesExisting() {
        String type = "FORUM_REPLY";
        UpdateNotificationPreferenceRequest request = new UpdateNotificationPreferenceRequest(true, false, true);

        NotificationPreferenceEntity existing = new NotificationPreferenceEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setType(type);
        existing.setInAppEnabled(false);
        existing.setEmailEnabled(true);
        existing.setPushEnabled(false);

        when(preferenceRepository.findByUserIdAndType(userId, type)).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any(NotificationPreferenceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceResponse result = preferenceService.updatePreference(userId, type, request);

        assertThat(result.isInAppEnabled()).isTrue();
        assertThat(result.isEmailEnabled()).isFalse();
        assertThat(result.isPushEnabled()).isTrue();

        verify(preferenceRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getId()).isEqualTo(existing.getId());
    }

    @Test
    void isInAppEnabledReturnsFalseWhenPrefDisabled() {
        String type = "FORUM_REPLY";
        NotificationPreferenceEntity pref = new NotificationPreferenceEntity();
        pref.setInAppEnabled(false);

        when(preferenceRepository.findByUserIdAndType(userId, type)).thenReturn(Optional.of(pref));

        assertThat(preferenceService.isInAppEnabled(userId, type)).isFalse();
    }

    @Test
    void isInAppEnabledReturnsDefaultTrueWhenNoPref() {
        String type = "FORUM_REPLY";

        when(preferenceRepository.findByUserIdAndType(userId, type)).thenReturn(Optional.empty());

        assertThat(preferenceService.isInAppEnabled(userId, type)).isTrue();
    }
}
