package project.lms_rikkei_edu.modules.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationListItemResponse;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationPageResponse;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationResponse;
import project.lms_rikkei_edu.modules.notification.entity.NotificationEntity;
import project.lms_rikkei_edu.modules.notification.repository.NotificationRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
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
    void getNotificationListUsesProjectionPageResponse() {
        UUID recipientId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        NotificationListItemResponse item = new NotificationListItemResponse(
                UUID.randomUUID(),
                "FORUM_REPLY",
                "Title",
                "Body",
                "FORUM_POST",
                UUID.randomUUID(),
                false,
                OffsetDateTime.now()
        );

        when(notificationRepository.findListItemsByRecipientId(recipientId, pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        NotificationPageResponse<NotificationListItemResponse> result = notificationService.getNotificationList(recipientId, pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Title");
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isLast()).isTrue();
        verify(notificationRepository).findListItemsByRecipientId(recipientId, pageable);
        verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDesc(eq(recipientId), any());
    }

    @Test
    void getUnreadCountDelegatesToRepository() {
        UUID recipientId = UUID.randomUUID();
        when(notificationRepository.countByRecipientIdAndReadFalse(recipientId)).thenReturn(3L);

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
    void sendLatestNotificationsUsesCacheHitWithoutQueryingDatabase() throws Exception {
        UUID recipientId = UUID.randomUUID();
        List<Map<String, Object>> cached = List.of(Map.of(
                "id", UUID.randomUUID().toString(),
                "type", "FORUM_POST",
                "title", "Cached title",
                "body", "Cached body",
                "read", false,
                "createdAt", OffsetDateTime.now().toString()
        ));
        when(redisService.get("notifications:latest:" + recipientId + ":size:5"))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(cached)));

        notificationService.sendLatestNotifications(recipientId, 5);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(sseEmitterRegistry).sendToUser(eq(recipientId), eq("LATEST_NOTIFICATIONS"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(Map.class);
        verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDesc(eq(recipientId), any());
    }

    private void stubNotificationBroadcastQueries(UUID recipientId) {
        when(redisService.get(any())).thenReturn(Optional.empty());
        when(notificationRepository.countByRecipientIdAndReadFalse(recipientId)).thenReturn(0L);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq(recipientId), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }
}
