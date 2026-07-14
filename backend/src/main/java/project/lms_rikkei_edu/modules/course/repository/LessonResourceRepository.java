package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonResourceRepository extends JpaRepository<LessonResource, UUID> {

    List<LessonResource> findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(UUID lessonId);

    /** Dùng cho picker "Tài liệu AI" — toàn bộ resource chưa xóa của khóa học, kèm sẵn lesson + chapter để lấy title. */
    @Query("SELECT r FROM LessonResource r JOIN FETCH r.lesson l JOIN FETCH l.chapter c " +
           "WHERE l.courseId = :courseId AND r.deletedAt IS NULL ORDER BY c.orderIndex, l.orderIndex, r.orderIndex")
    List<LessonResource> findAllByCourseIdWithLessonAndChapter(UUID courseId);

    /** Dùng cho instructor list — ẩn những resource đang chờ xóa */
    List<LessonResource> findAllByLessonIdAndDeletedAtIsNullAndPendingDeleteFalseOrderByOrderIndexAsc(UUID lessonId);

    List<LessonResource> findAllByCourseIdAndPendingDeleteTrue(UUID courseId);

    List<LessonResource> findAllByCourseIdAndIsNewInUpdateTrue(UUID courseId);

    boolean existsByCourseIdAndPendingDeleteTrue(UUID courseId);

    boolean existsByCourseIdAndIsNewInUpdateTrue(UUID courseId);

    void deleteAllByLessonIdIn(List<UUID> lessonIds);

    boolean existsByS3KeyAndDeletedAtIsNull(String s3Key);

    /**
     * Tìm tài liệu ĐÃ soft-delete (deleted_at NOT NULL) cùng lesson và tên hiển thị — dùng khi
     * rollback cần "hồi sinh" 1 tài liệu đã bị admin duyệt xóa riêng lẻ (khác với trường hợp cả
     * lesson bị xóa — xem LessonRepository.findSoftDeletedByChapterIdAndOrderIndex). Bắt buộc
     * dùng native query vì {@code @SQLRestriction("deleted_at IS NULL")} trên LessonResource áp
     * dụng cho MỌI JPQL/Criteria query, kể cả query tự viết — chỉ native query mới bỏ qua được.
     */
    @Query(value = "SELECT * FROM lesson_resources WHERE lesson_id = :lessonId "
            + "AND COALESCE(display_name, original_filename) = :displayName "
            + "AND deleted_at IS NOT NULL ORDER BY deleted_at DESC LIMIT 1", nativeQuery = true)
    Optional<LessonResource> findSoftDeletedByLessonIdAndDisplayName(@Param("lessonId") UUID lessonId, @Param("displayName") String displayName);
}
