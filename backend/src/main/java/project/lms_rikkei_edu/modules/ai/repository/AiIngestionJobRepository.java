package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.ai.entity.AiIngestionJob;
import project.lms_rikkei_edu.modules.ai.entity.enums.JobStatus;

import java.util.List;
import java.util.UUID;

public interface AiIngestionJobRepository extends JpaRepository<AiIngestionJob, UUID> {

    List<AiIngestionJob> findBySourceId(UUID sourceId);

    List<AiIngestionJob> findByStatus(JobStatus status);
}
