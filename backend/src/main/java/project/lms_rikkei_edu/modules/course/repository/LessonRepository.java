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

    /**
     * Tìm bài giảng ĐÃ soft-delete tại đúng vị trí trong 1 chương — dùng khi rollback cần "hồi
     * sinh" lại bài đã bị admin duyệt xóa (thay vì tạo mới trắng tay qua buildDraftLesson, mất hết
     * tài liệu gốc — tài liệu vẫn còn nguyên trong DB nhờ soft-delete, chỉ cần gắn lại lessonId).
     * Bắt buộc dùng native query vì {@code @SQLRestriction("deleted_at IS NULL")} trên Lesson áp
     * dụng cho MỌI JPQL/Criteria query, kể cả query tự viết — chỉ native query mới bỏ qua được.
     */
    @Query(value = "SELECT * FROM lessons WHERE chapter_id = :chapterId AND order_index = :orderIndex "
            + "AND deleted_at IS NOT NULL ORDER BY deleted_at DESC LIMIT 1", nativeQuery = true)
    Optional<Lesson> findSoftDeletedByChapterIdAndOrderIndex(@Param("chapterId") UUID chapterId, @Param("orderIndex") Integer orderIndex);
}
