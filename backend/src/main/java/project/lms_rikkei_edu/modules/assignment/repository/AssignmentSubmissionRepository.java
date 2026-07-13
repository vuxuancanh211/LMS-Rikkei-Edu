package project.lms_rikkei_edu.modules.assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentSubmissionEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmissionEntity, UUID> {

    Optional<AssignmentSubmissionEntity> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    List<AssignmentSubmissionEntity> findByAssignmentIdAndStudentIdOrderBySubmissionNumberDesc(UUID assignmentId, UUID studentId);

    long countByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    List<AssignmentSubmissionEntity> findByAssignmentIdOrderBySubmittedAtDesc(UUID assignmentId);

    List<AssignmentSubmissionEntity> findByAssignmentIdAndStatusOrderBySubmittedAtDesc(UUID assignmentId, String status);

    @Modifying
    @Query("UPDATE AssignmentSubmissionEntity s SET s.scorePublishedAt = :now WHERE s.id IN :ids")
    int batchPublishScores(@Param("ids") List<UUID> ids, @Param("now") OffsetDateTime now);
}
