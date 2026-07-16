package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.ai.entity.AiConversation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByStudentIdAndCourseIdOrderByLastMessageAtDesc(UUID studentId, UUID courseId);

    List<AiConversation> findByStudentIdOrderByLastMessageAtDesc(UUID studentId);

    /* Dùng khi xóa hẳn 1 lesson — cần tìm hết hội thoại gắn với lesson đó để dọn trước khi xóa,
       giải phóng FK ai_conversations.lesson_id -> lessons. */
    List<AiConversation> findByLessonIdIn(Collection<UUID> lessonIds);

    /* deleteAllById() mặc định là entity-based (find rồi remove()) — không đảm bảo sinh ra một
       câu DELETE thực thi ngay khi entity đã nằm sẵn trong persistence context (xem
       AiSourceRepository.deleteAllByIdInBulk). Dùng bulk JPQL để đảm bảo chắc chắn. */
    @Modifying
    @Query("DELETE FROM AiConversation c WHERE c.id IN :ids")
    void deleteAllByIdInBulk(@Param("ids") List<UUID> ids);
}
