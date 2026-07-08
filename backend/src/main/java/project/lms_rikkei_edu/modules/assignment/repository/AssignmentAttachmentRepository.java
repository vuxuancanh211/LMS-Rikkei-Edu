package project.lms_rikkei_edu.modules.assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentAttachmentEntity;

import java.util.List;
import java.util.UUID;

public interface AssignmentAttachmentRepository extends JpaRepository<AssignmentAttachmentEntity, UUID> {

    List<AssignmentAttachmentEntity> findByAssignmentIdOrderByOrderIndexAsc(UUID assignmentId);

    long countByAssignmentId(UUID assignmentId);
}
