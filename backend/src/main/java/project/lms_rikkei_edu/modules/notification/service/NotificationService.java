package project.lms_rikkei_edu.modules.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
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
    public Page<NotificationEntity> getNotifications(UUID recipientId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
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
    public NotificationEntity createNotification(
            UUID recipientId, String type, String title, String body,
            String referenceType, UUID referenceId, UUID actorId, String actorName
    ) {
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
        notif.setPriority("NORMAL");
        notif.setRead(false);
        notif.setEmailSent(false);
        notif.setPushSent(false);
        notif.setCreatedAt(now);
        NotificationEntity saved = notificationRepository.save(notif);

        sseEmitterRegistry.sendToUser(recipientId, "NOTIFICATION", Map.of(
                "type", "NEW_NOTIFICATION",
                "notification", saved
        ));

        return saved;
    }
}
