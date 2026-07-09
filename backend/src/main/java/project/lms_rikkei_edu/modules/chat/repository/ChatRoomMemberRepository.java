package project.lms_rikkei_edu.modules.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomMemberEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMemberEntity, UUID> {

    // Kiểm tra user có trong room không
    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    // Lấy member trong room
    Optional<ChatRoomMemberEntity> findByRoomIdAndUserId(UUID roomId, UUID userId);

    // Lấy tất cả member của room
    List<ChatRoomMemberEntity> findAllByRoomId(UUID roomId);

    // Đếm số member trong room
    long countByRoomId(UUID roomId);

    @Modifying
    @Query("UPDATE ChatRoomMemberEntity m SET m.lastReadMessage = null WHERE m.room.id = :roomId")
    void clearLastReadMessagesByRoomId(@Param("roomId") UUID roomId);

    @Modifying
    @Query("DELETE FROM ChatRoomMemberEntity m WHERE m.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") UUID roomId);
}
