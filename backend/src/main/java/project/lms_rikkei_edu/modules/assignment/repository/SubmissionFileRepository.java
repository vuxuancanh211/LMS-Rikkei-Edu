package project.lms_rikkei_edu.modules.assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.assignment.entity.SubmissionFileEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFileEntity, UUID> {

    List<SubmissionFileEntity> findBySubmissionIdOrderByOrderIndexAsc(UUID submissionId);

    List<SubmissionFileEntity> findBySubmissionIdInOrderByOrderIndexAsc(Set<UUID> submissionIds);

    List<SubmissionFileEntity> findBySubmissionIdIn(Set<UUID> submissionIds);

    void deleteBySubmissionIdIn(Set<UUID> submissionIds);
}
