package project.lms_rikkei_edu.modules.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationListItemResponse;
import project.lms_rikkei_edu.modules.notification.entity.NotificationEntity;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    @Query(
            value = """
                    select new project.lms_rikkei_edu.modules.notification.dto.response.NotificationListItemResponse(
                        n.id,
                        n.type,
                        n.title,
                        n.body,
                        n.referenceType,
                        n.referenceId,
                        n.read,
                        n.createdAt
                    )
                    from NotificationEntity n
                    where n.recipientId = :recipientId
                    order by n.createdAt desc
                    """,
            countQuery = "select count(n.id) from NotificationEntity n where n.recipientId = :recipientId"
    )
    Page<NotificationListItemResponse> findListItemsByRecipientId(@Param("recipientId") UUID recipientId, Pageable pageable);

    Optional<NotificationEntity> findByIdempotencyKey(String idempotencyKey);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    @Modifying
    @Query("update NotificationEntity n set n.read = true, n.readAt = current_timestamp where n.id = :id and n.recipientId = :recipientId")
    int markAsRead(@Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Modifying
    @Query("update NotificationEntity n set n.read = true, n.readAt = current_timestamp where n.recipientId = :recipientId and n.read = false")
    int markAllAsRead(@Param("recipientId") UUID recipientId);
}
