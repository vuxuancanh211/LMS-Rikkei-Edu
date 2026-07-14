package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.ai.entity.AiMessage;

import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<AiMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    List<AiMessage> findByConversationIdIn(List<UUID> conversationIds);

    /* Bulk @Modifying thay vì derived "deleteBy" entity-based — xem AiSourceRepository. */
    @Modifying
    @Query("DELETE FROM AiMessage m WHERE m.conversationId IN :conversationIds")
    void deleteByConversationIdIn(@Param("conversationIds") List<UUID> conversationIds);
}
