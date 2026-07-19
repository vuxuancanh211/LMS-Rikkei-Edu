package project.lms_rikkei_edu.modules.assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    List<AssignmentEntity> findByCourseIdOrderByCreatedAtDesc(UUID courseId);

    List<AssignmentEntity> findByCourseIdInOrderByCreatedAtDesc(List<UUID> courseIds);

    List<AssignmentEntity> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    Optional<AssignmentEntity> findByIdAndCourseId(UUID id, UUID courseId);

    @Query("SELECT a FROM AssignmentEntity a WHERE a.courseId = :courseId AND a.status = 'PUBLISHED'")
    List<AssignmentEntity> findPublishedByCourseId(@Param("courseId") UUID courseId);
}
