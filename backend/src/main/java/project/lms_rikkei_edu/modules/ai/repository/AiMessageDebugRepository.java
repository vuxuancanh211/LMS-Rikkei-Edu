package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.ai.entity.AiMessageDebug;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiMessageDebugRepository extends JpaRepository<AiMessageDebug, UUID> {

    Optional<AiMessageDebug> findByMessageId(UUID messageId);

    /* Bulk @Modifying thay vì derived "deleteBy" entity-based — xem AiSourceRepository. */
    @Modifying
    @Query("DELETE FROM AiMessageDebug d WHERE d.messageId IN :messageIds")
    void deleteByMessageIdIn(@Param("messageIds") List<UUID> messageIds);
}
