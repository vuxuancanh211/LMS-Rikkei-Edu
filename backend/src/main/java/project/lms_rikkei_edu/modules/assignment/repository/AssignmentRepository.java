package project.lms_rikkei_edu.modules.assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    List<AssignmentEntity> findByCourseIdOrderByCreatedAtDesc(UUID courseId);

    List<AssignmentEntity> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    Optional<AssignmentEntity> findByIdAndCourseId(UUID id, UUID courseId);
}
