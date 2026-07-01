package project.lms_rikkei_edu.modules.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationResponse;
import project.lms_rikkei_edu.modules.notification.entity.NotificationEntity;
import project.lms_rikkei_edu.modules.notification.repository.NotificationRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(UUID recipientId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID recipientId) {
        notificationRepository.markAsRead(notificationId, recipientId);
    }

    @Transactional
    public void markAllAsRead(UUID recipientId) {
        notificationRepository.markAllAsRead(recipientId);
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

        return response;
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
