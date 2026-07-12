package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.course.entity.Lesson;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findAllByChapterIdOrderByOrderIndexAsc(UUID chapterId);

    @Query("SELECT COALESCE(MAX(l.orderIndex), 0) FROM Lesson l WHERE l.chapter.id = :chapterId")
    int findMaxOrderIndexByChapterId(@Param("chapterId") UUID chapterId);

    Optional<Lesson> findByIdAndCourseId(UUID id, UUID courseId);

    long countByCourseId(UUID courseId);

    Optional<Lesson> findByQuizId(UUID quizId);

    /** Nạp thêm resources cho các lesson đã load sẵn của 1 khóa học — xem ghi chú ở CourseRepository.findByIdWithFullContent. */
    @Query("SELECT DISTINCT l FROM Lesson l LEFT JOIN FETCH l.resources WHERE l.courseId = :courseId")
    List<Lesson> findAllWithResourcesByCourseId(@Param("courseId") UUID courseId);
}
