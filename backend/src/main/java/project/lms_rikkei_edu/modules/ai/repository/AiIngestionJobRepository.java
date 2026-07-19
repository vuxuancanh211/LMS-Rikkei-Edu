package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.ai.entity.AiIngestionJob;
import project.lms_rikkei_edu.modules.ai.entity.enums.JobStatus;

import java.util.List;
import java.util.UUID;

public interface AiIngestionJobRepository extends JpaRepository<AiIngestionJob, UUID> {

    List<AiIngestionJob> findBySourceId(UUID sourceId);

    List<AiIngestionJob> findByStatus(JobStatus status);

    /* Bulk @Modifying thay vì derived "deleteBy" entity-based — xem AiSourceRepository. */
    @Modifying
    @Query("DELETE FROM AiIngestionJob j WHERE j.sourceId IN :sourceIds")
    void deleteBySourceIdIn(@Param("sourceIds") List<UUID> sourceIds);
}
