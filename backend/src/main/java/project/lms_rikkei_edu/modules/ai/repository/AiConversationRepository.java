package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.ai.entity.AiConversation;

import java.util.List;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByStudentIdAndCourseIdOrderByLastMessageAtDesc(UUID studentId, UUID courseId);

    List<AiConversation> findByStudentIdOrderByLastMessageAtDesc(UUID studentId);
}
