package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;

import java.util.List;
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
}
