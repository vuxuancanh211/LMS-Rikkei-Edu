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

    @Query("SELECT ce.studentId FROM CourseEnrollmentEntity ce WHERE ce.courseId = :courseId")
    List<UUID> findStudentIdsByCourseId(@Param("courseId") UUID courseId);

    @Query("SELECT u.id, u.email, u.fullName, u.phoneNumber, u.avatarUrl, c.id, c.title " +
           "FROM CourseEnrollmentEntity ce " +
           "JOIN UserEntity u ON u.id = ce.studentId " +
           "JOIN Course c ON c.id = ce.courseId " +
           "WHERE (:courseId IS NULL OR ce.courseId = :courseId) " +
           "AND u.deletedAt IS NULL " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM GroupMemberEntity gm " +
           "  JOIN StudyGroupEntity sg ON sg.id = gm.group.id " +
           "  WHERE gm.student.id = u.id AND sg.course.id = c.id" +
           ")")
    List<Object[]> findUnassignedStudentsWithCourseInfo(@Param("courseId") UUID courseId);

    @Query("SELECT ce.studentId FROM CourseEnrollmentEntity ce WHERE ce.courseId = :courseId AND ce.studentId IN :studentIds")
    List<UUID> findEnrolledStudentIds(@Param("courseId") UUID courseId, @Param("studentIds") List<UUID> studentIds);
}
