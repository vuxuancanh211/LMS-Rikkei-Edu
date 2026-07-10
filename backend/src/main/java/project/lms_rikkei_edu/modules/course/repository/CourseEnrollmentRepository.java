package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.course.entity.CourseEnrollmentEntity;

import java.util.UUID;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollmentEntity, UUID> {

    boolean existsByCourseIdAndStudentId(UUID courseId, UUID studentId);
}
