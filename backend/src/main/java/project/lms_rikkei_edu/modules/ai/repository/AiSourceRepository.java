package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;

import java.util.List;
import java.util.UUID;

public interface AiSourceRepository extends JpaRepository<AiSource, UUID> {

    List<AiSource> findByCourseIdAndDeletedAtIsNull(UUID courseId);

    List<AiSource> findByCourseIdAndIngestStatusAndDeletedAtIsNull(UUID courseId, IngestStatus ingestStatus);
}
