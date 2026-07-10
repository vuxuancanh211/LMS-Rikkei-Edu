package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.course.entity.CourseEnrollmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollmentEntity, UUID> {

    List<CourseEnrollmentEntity> findAllByStudentId(UUID studentId);

    List<CourseEnrollmentEntity> findAllByCourseId(UUID courseId);

    Optional<CourseEnrollmentEntity> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    boolean existsByCourseIdAndStudentId(UUID courseId, UUID studentId);

    @Query("SELECT ce.courseId FROM CourseEnrollmentEntity ce WHERE ce.studentId = :studentId")
    List<UUID> findCourseIdsByStudentId(@Param("studentId") UUID studentId);
}
