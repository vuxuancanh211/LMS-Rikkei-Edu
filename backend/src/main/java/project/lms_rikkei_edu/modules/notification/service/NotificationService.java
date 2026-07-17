package project.lms_rikkei_edu.modules.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final long LATEST_NOTIFICATIONS_CACHE_TTL_SECONDS = 600;

    private final NotificationRepository notificationRepository;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(UUID recipientId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse<NotificationListItemResponse> getNotificationList(UUID recipientId, Pageable pageable) {
        return NotificationPageResponse.from(notificationRepository.findListItemsByRecipientId(recipientId, pageable));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID recipientId) {
        notificationRepository.markAsRead(notificationId, recipientId);
        evictLatestNotifications(recipientId);
        sendUnreadCount(recipientId);
        sendLatestNotifications(recipientId, 5);
    }

    @Transactional
    public void markAllAsRead(UUID recipientId) {
        notificationRepository.markAllAsRead(recipientId);
        evictLatestNotifications(recipientId);
        sendUnreadCount(recipientId);
        sendLatestNotifications(recipientId, 5);
    }

    @Transactional
    public NotificationResponse createNotification(
            UUID recipientId, String type, String title, String body,
            String referenceType, UUID referenceId, UUID actorId, String actorName
    ) {
        return createNotification(recipientId, type, title, body, referenceType, referenceId, actorId, actorName, null);
    }

    @Transactional
    public NotificationResponse createNotification(
            UUID recipientId, String type, String title, String body,
            String referenceType, UUID referenceId, UUID actorId, String actorName,
            String idempotencyKey
    ) {
        if (idempotencyKey != null) {
            var existing = notificationRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        NotificationEntity notif = new NotificationEntity();
        notif.setId(UUID.randomUUID());
        notif.setRecipientId(recipientId);
        notif.setType(type);
        notif.setTitle(title);
        notif.setBody(body);
        notif.setReferenceType(referenceType);
        notif.setReferenceId(referenceId);
        notif.setActorId(actorId);
        notif.setActorName(actorName);
        notif.setIdempotencyKey(idempotencyKey);
        notif.setPriority("NORMAL");
        notif.setRead(false);
        notif.setEmailSent(false);
        notif.setPushSent(false);
        notif.setCreatedAt(now);
        NotificationEntity saved = notificationRepository.save(notif);

        NotificationResponse response = toResponse(saved);

        sseEmitterRegistry.sendToUser(recipientId, "NOTIFICATION", Map.of(
                "type", "NEW_NOTIFICATION",
                "notification", response
        ));
        evictLatestNotifications(recipientId);
        sendUnreadCount(recipientId);
        sendLatestNotifications(recipientId, 5);

        return response;
    }

    public void sendUnreadCount(UUID recipientId) {
        sseEmitterRegistry.sendToUser(recipientId, "UNREAD_COUNT", Map.of(
                "count", getUnreadCount(recipientId)
        ));
    }

    public void sendLatestNotifications(UUID recipientId, int size) {
        sseEmitterRegistry.sendToUser(recipientId, "LATEST_NOTIFICATIONS", Map.of(
                "notifications", getCachedLatestNotifications(recipientId, size)
        ));
    }

    private List<?> getCachedLatestNotifications(UUID recipientId, int size) {
        String key = latestNotificationsCacheKey(recipientId, size);
        try {
            Object cached = redisService.get(key).orElse(null);
            if (cached instanceof String json) {
                List<Map<String, Object>> latest = objectMapper.readValue(json, new TypeReference<>() {});
                log.info("Latest notifications cache hit: key={}", key);
                return latest;
            }
            if (cached != null) {
                log.info("Latest notifications cache ignored: key={}, cachedType={}", key, cached.getClass().getName());
            }
        } catch (Exception exception) {
            log.info("Latest notifications cache read failed: key={}, error={}", key, exception.getMessage());
            // Redis is optional; fall back to database if cache is unavailable.
        }

        log.info("Latest notifications cache miss: key={}", key);
        List<NotificationResponse> latest = getNotifications(recipientId, PageRequest.of(0, size)).getContent();
        try {
            redisService.set(key, objectMapper.writeValueAsString(latest), LATEST_NOTIFICATIONS_CACHE_TTL_SECONDS);
            log.info("Latest notifications cache set: key={}, ttlSeconds={}", key, LATEST_NOTIFICATIONS_CACHE_TTL_SECONDS);
        } catch (Exception exception) {
            log.info("Latest notifications cache write failed: key={}, error={}", key, exception.getMessage());
            // Ignore cache write failures.
        }
        return latest;
    }

    private void evictLatestNotifications(UUID recipientId) {
        try {
            String key = latestNotificationsCacheKey(recipientId, 5);
            redisService.delete(key);
            log.info("Latest notifications cache evicted: key={}", key);
        } catch (RuntimeException ignored) {
            // Ignore cache eviction failures.
        }
    }

    private String latestNotificationsCacheKey(UUID recipientId, int size) {
        return RedisKeyConstants.NOTIFICATIONS_LATEST + recipientId + ":size:" + size;
    }

    public NotificationResponse toResponse(NotificationEntity entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .recipientId(entity.getRecipientId())
                .type(entity.getType())
                .title(entity.getTitle())
                .body(entity.getBody())
                .referenceType(entity.getReferenceType())
                .referenceId(entity.getReferenceId())
                .actorId(entity.getActorId())
                .actorName(entity.getActorName())
                .priority(entity.getPriority())
                .read(Boolean.TRUE.equals(entity.getRead()))
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
