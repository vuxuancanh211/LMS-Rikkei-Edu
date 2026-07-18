package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.course.entity.CourseProgressEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseProgressRepository extends JpaRepository<CourseProgressEntity, UUID> {

    Optional<CourseProgressEntity> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    List<CourseProgressEntity> findByStudentIdAndCourseIdIn(UUID studentId, List<UUID> courseIds);

    List<CourseProgressEntity> findByStudentIdInAndCourseIdIn(List<UUID> studentIds, List<UUID> courseIds);

    List<CourseProgressEntity> findByCourseId(UUID courseId);

    void deleteByCourseIdAndStudentIdIn(UUID courseId, List<UUID> studentIds);

    void deleteByCourseIdAndStudentId(UUID courseId, UUID studentId);
}
