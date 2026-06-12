package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.ai.entity.AiMessageDebug;

import java.util.Optional;
import java.util.UUID;

public interface AiMessageDebugRepository extends JpaRepository<AiMessageDebug, UUID> {

    Optional<AiMessageDebug> findByMessageId(UUID messageId);
}
