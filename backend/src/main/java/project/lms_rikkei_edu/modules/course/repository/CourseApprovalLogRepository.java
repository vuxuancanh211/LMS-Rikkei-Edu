package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.course.entity.CourseApprovalLog;

import java.util.List;
import java.util.UUID;

public interface CourseApprovalLogRepository extends JpaRepository<CourseApprovalLog, UUID> {

    List<CourseApprovalLog> findByCourseIdOrderByCreatedAtAsc(UUID courseId);
}
