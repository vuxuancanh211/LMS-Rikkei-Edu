package project.lms_rikkei_edu.modules.chat.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    // Lấy tin nhắn theo room, phân trang, mới nhất trước
    @Query("""
        SELECT m FROM ChatMessageEntity m
        WHERE m.room.id = :roomId
        ORDER BY m.createdAt DESC
        """)
    Page<ChatMessageEntity> findByRoomId(
            @Param("roomId") UUID roomId,
            Pageable pageable
    );

    // Lấy tin nhắn cuối cùng của room
    @Query("""
        SELECT m FROM ChatMessageEntity m
        WHERE m.room.id = :roomId
        AND m.deleted = false
        ORDER BY m.createdAt DESC
        LIMIT 1
        """)
    Optional<ChatMessageEntity> findLastMessageByRoomId(@Param("roomId") UUID roomId);

    // Đếm tin chưa đọc — sau lastReadMessage của user
    @Query("""
        SELECT COUNT(m) FROM ChatMessageEntity m
        WHERE m.room.id = :roomId
        AND m.deleted = false
        AND m.createdAt > :after
        """)
        int countUnreadMessages(
                @Param("roomId") UUID roomId,
                @Param("after") OffsetDateTime after
        );
}
