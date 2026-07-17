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

    long countByCourseId(UUID courseId);

    @Query("SELECT ce.courseId FROM CourseEnrollmentEntity ce WHERE ce.studentId = :studentId")
    List<UUID> findCourseIdsByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT ce.studentId FROM CourseEnrollmentEntity ce WHERE ce.courseId = :courseId")
    List<UUID> findStudentIdsByCourseId(@Param("courseId") UUID courseId);

    @Query("SELECT ce.studentId FROM CourseEnrollmentEntity ce WHERE ce.courseId = :courseId AND ce.studentId IN :studentIds")
    List<UUID> findEnrolledStudentIds(@Param("courseId") UUID courseId, @Param("studentIds") List<UUID> studentIds);
}
