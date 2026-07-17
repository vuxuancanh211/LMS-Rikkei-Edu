package project.lms_rikkei_edu.modules.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationResponse;
import project.lms_rikkei_edu.modules.notification.entity.NotificationEntity;
import project.lms_rikkei_edu.modules.notification.repository.NotificationRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
    @Mock
    private RedisService redisService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        notificationService = new NotificationService(notificationRepository, sseEmitterRegistry, redisService, objectMapper);
    }

    @Test
    void createNotificationPersistsDefaultsAndSendsSse() {
        UUID recipientId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> {
            NotificationEntity entity = invocation.getArgument(0);
            entity.setCreatedAt(OffsetDateTime.now());
            return entity;
        });
        stubNotificationBroadcastQueries(recipientId);

        NotificationResponse saved = notificationService.createNotification(
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
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(sseEmitterRegistry).sendToUser(eq(recipientId), eq("NOTIFICATION"), payloadCaptor.capture());
    }

    @Test
    void delegatesReadOperationsToRepository() {
        UUID recipientId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        NotificationEntity entity = new NotificationEntity();
        entity.setId(UUID.randomUUID());
        entity.setRecipientId(recipientId);
        entity.setType("FORUM_REPLY");
        entity.setTitle("Title");
        entity.setPriority("NORMAL");
        entity.setRead(false);
        entity.setEmailSent(false);
        entity.setPushSent(false);
        entity.setCreatedAt(OffsetDateTime.now());
        Page<NotificationEntity> entityPage = new PageImpl<>(List.of(entity));

        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)).thenReturn(entityPage);
        when(notificationRepository.countByRecipientIdAndReadFalse(recipientId)).thenReturn(3L);

        Page<NotificationResponse> result = notificationService.getNotifications(recipientId, pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Title");
        assertThat(notificationService.getUnreadCount(recipientId)).isEqualTo(3L);
    }

    @Test
    void delegatesMarkReadOperationsToRepository() {
        UUID recipientId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        stubNotificationBroadcastQueries(recipientId);

        notificationService.markAsRead(notificationId, recipientId);
        notificationService.markAllAsRead(recipientId);

        verify(notificationRepository).markAsRead(notificationId, recipientId);
        verify(notificationRepository).markAllAsRead(recipientId);
    }

    @Test
    void createNotificationWithIdempotencyKeySkipsDuplicates() {
        UUID recipientId = UUID.randomUUID();
        String idempotencyKey = "key-1";
        NotificationEntity existing = new NotificationEntity();
        existing.setId(UUID.randomUUID());
        existing.setRecipientId(recipientId);
        existing.setType("FORUM_REPLY");
        existing.setTitle("Existing");
        existing.setPriority("NORMAL");
        existing.setRead(false);
        existing.setEmailSent(false);
        existing.setPushSent(false);
        existing.setCreatedAt(OffsetDateTime.now());

        when(notificationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        NotificationResponse result = notificationService.createNotification(
                recipientId, "FORUM_REPLY", "New", "Body",
                "FORUM_POST", UUID.randomUUID(), UUID.randomUUID(), "Actor", idempotencyKey
        );

        assertThat(result.getTitle()).isEqualTo("Existing");
        verify(notificationRepository).findByIdempotencyKey(idempotencyKey);
    }

    @Test
    void createNotificationWithoutIdempotencyKeySaves() {
        UUID recipientId = UUID.randomUUID();
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> {
            NotificationEntity entity = invocation.getArgument(0);
            entity.setCreatedAt(OffsetDateTime.now());
            return entity;
        });
        stubNotificationBroadcastQueries(recipientId);

        NotificationResponse result = notificationService.createNotification(
                recipientId, "FORUM_REPLY", "Test", "Body",
                "FORUM_POST", UUID.randomUUID(), UUID.randomUUID(), "Actor"
        );

        assertThat(result.getTitle()).isEqualTo("Test");
        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    private void stubNotificationBroadcastQueries(UUID recipientId) {
        when(redisService.get(any())).thenReturn(Optional.empty());
        when(notificationRepository.countByRecipientIdAndReadFalse(recipientId)).thenReturn(0L);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq(recipientId), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }
}
