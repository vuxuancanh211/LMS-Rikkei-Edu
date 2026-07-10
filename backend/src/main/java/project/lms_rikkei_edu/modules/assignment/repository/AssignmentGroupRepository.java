package project.lms_rikkei_edu.modules.assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.assignment.entity.AssignmentGroupEntity;

import java.util.List;
import java.util.UUID;

public interface AssignmentGroupRepository extends JpaRepository<AssignmentGroupEntity, UUID> {

    List<AssignmentGroupEntity> findByAssignmentId(UUID assignmentId);

    void deleteByAssignmentId(UUID assignmentId);
}
