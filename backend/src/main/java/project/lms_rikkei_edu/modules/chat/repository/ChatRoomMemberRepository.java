package project.lms_rikkei_edu.modules.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
