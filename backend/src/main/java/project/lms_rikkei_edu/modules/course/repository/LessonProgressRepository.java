package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.course.entity.LessonProgressEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository extends JpaRepository<LessonProgressEntity, UUID> {

    List<LessonProgressEntity> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Optional<LessonProgressEntity> findByStudentIdAndLessonId(UUID studentId, UUID lessonId);

    void deleteByCourseIdAndStudentIdIn(UUID courseId, List<UUID> studentIds);

    void deleteByCourseIdAndStudentId(UUID courseId, UUID studentId);
}
