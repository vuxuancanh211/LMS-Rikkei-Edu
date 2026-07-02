package project.lms_rikkei_edu.modules.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageReactionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageReactionRepository
        extends JpaRepository<ChatMessageReactionEntity, UUID> {

    // Kiểm tra user đã react emoji này chưa
    boolean existsByMessageIdAndUserIdAndEmoji(
            UUID messageId, UUID userId, String emoji
    );

    // Lấy reaction theo message
    List<ChatMessageReactionEntity> findAllByMessageId(UUID messageId);

    // Xóa reaction cụ thể
    void deleteByMessageIdAndUserIdAndEmoji(
            UUID messageId, UUID userId, String emoji
    );

    void deleteAllByMessageIdAndUserId(UUID messageId, UUID userId);

    // Đếm reaction theo emoji — dùng để build Map<String, Long>
    @Query("""
        SELECT r.emoji, COUNT(r)
        FROM ChatMessageReactionEntity r
        WHERE r.message.id = :messageId
        GROUP BY r.emoji
        """)
    List<Object[]> countReactionsByMessageId(@Param("messageId") UUID messageId);
}
