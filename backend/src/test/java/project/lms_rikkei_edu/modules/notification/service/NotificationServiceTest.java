package project.lms_rikkei_edu.modules.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.notification.entity.NotificationEntity;
import project.lms_rikkei_edu.modules.notification.repository.NotificationRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, sseEmitterRegistry);
    }

    @Test
    void createNotificationPersistsDefaultsAndSendsSse() {
        UUID recipientId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationEntity saved = notificationService.createNotification(
                recipientId,
                "FORUM_REPLY",
                "New reply",
                "Body",
                "FORUM_POST",
                referenceId,
                actorId,
                "Actor"
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRecipientId()).isEqualTo(recipientId);
        assertThat(saved.getType()).isEqualTo("FORUM_REPLY");
        assertThat(saved.getPriority()).isEqualTo("NORMAL");
        assertThat(saved.getRead()).isFalse();
        assertThat(saved.getEmailSent()).isFalse();
        assertThat(saved.getPushSent()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sseEmitterRegistry).sendToUser(eq(recipientId), eq("NOTIFICATION"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).containsEntry("type", "NEW_NOTIFICATION");
        assertThat(payloadCaptor.getValue()).containsEntry("notification", saved);
    }

    @Test
    void delegatesReadOperationsToRepository() {
        UUID recipientId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        Page<NotificationEntity> page = new PageImpl<>(List.of(new NotificationEntity()));

        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)).thenReturn(page);
        when(notificationRepository.countByRecipientIdAndReadFalse(recipientId)).thenReturn(3L);

        assertThat(notificationService.getNotifications(recipientId, pageable)).isSameAs(page);
        assertThat(notificationService.getUnreadCount(recipientId)).isEqualTo(3L);
    }

    @Test
    void delegatesMarkReadOperationsToRepository() {
        UUID recipientId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        notificationService.markAsRead(notificationId, recipientId);
        notificationService.markAllAsRead(recipientId);

        verify(notificationRepository).markAsRead(notificationId, recipientId);
        verify(notificationRepository).markAllAsRead(recipientId);
    }
}
