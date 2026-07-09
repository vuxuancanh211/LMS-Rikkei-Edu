package project.lms_rikkei_edu.modules.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoomEntity, UUID> {

    // Tìm room theo group
    Optional<ChatRoomEntity> findByGroupId(UUID groupId);

    // Kiểm tra room đã tồn tại cho group chưa
    boolean existsByGroupId(UUID groupId);

    // Lấy tất cả room mà user là thành viên
    @Query("""
        SELECT r FROM ChatRoomEntity r
        JOIN r.members m
        WHERE m.user.id = :userId
        AND r.active = true
        ORDER BY r.lastMessageAt DESC NULLS LAST
        """)
    List<ChatRoomEntity> findAllByMemberId(@Param("userId") UUID userId);
}
