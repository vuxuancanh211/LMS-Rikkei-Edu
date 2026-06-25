package project.lms_rikkei_edu.modules.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.forum.entity.ForumReportEntity;

import java.util.Optional;
import java.util.UUID;

public interface ForumReportRepository extends JpaRepository<ForumReportEntity, UUID> {

    Optional<ForumReportEntity> findByTargetTypeAndTargetIdAndReporterId(String targetType, UUID targetId, UUID reporterId);
}
