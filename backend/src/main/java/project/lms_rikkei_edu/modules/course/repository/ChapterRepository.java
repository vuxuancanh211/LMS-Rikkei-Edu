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

    /**
     * Tìm chương ĐÃ soft-delete tại đúng vị trí — dùng khi rollback cần "hồi sinh" lại 1 chương đã
     * bị admin duyệt xóa (thay vì tạo mới trắng tay qua buildDraftLesson, mất hết lessons/resources
     * gốc). Bắt buộc dùng native query vì {@code @SQLRestriction("deleted_at IS NULL")} trên Chapter
     * áp dụng cho MỌI JPQL/Criteria query, kể cả query tự viết — chỉ native query mới bỏ qua được.
     */
    @Query(value = "SELECT * FROM chapters WHERE course_id = :courseId AND order_index = :orderIndex "
            + "AND deleted_at IS NOT NULL ORDER BY deleted_at DESC LIMIT 1", nativeQuery = true)
    Optional<Chapter> findSoftDeletedByCourseIdAndOrderIndex(@Param("courseId") UUID courseId, @Param("orderIndex") Integer orderIndex);
}
