package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.ai.entity.AiMessage;

import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<AiMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);
}
