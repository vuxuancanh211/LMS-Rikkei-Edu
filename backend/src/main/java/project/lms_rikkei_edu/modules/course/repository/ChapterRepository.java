package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.course.entity.Chapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findAllByCourseIdOrderByOrderIndexAsc(UUID courseId);

    @Query("SELECT COALESCE(MAX(c.orderIndex), 0) FROM Chapter c WHERE c.course.id = :courseId")
    int findMaxOrderIndexByCourseId(@Param("courseId") UUID courseId);

    Optional<Chapter> findByIdAndCourseId(UUID id, UUID courseId);

    /** Nạp thêm lessons cho các chapter đã load sẵn của 1 khóa học — xem ghi chú ở CourseRepository.findByIdWithFullContent. */
    @Query("SELECT DISTINCT ch FROM Chapter ch LEFT JOIN FETCH ch.lessons WHERE ch.course.id = :courseId")
    List<Chapter> findAllWithLessonsByCourseId(@Param("courseId") UUID courseId);
}
