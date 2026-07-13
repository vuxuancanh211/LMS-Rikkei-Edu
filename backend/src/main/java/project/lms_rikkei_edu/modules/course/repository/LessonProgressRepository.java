package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.course.entity.LessonProgressEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository extends JpaRepository<LessonProgressEntity, UUID> {

    List<LessonProgressEntity> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Optional<LessonProgressEntity> findByStudentIdAndLessonId(UUID studentId, UUID lessonId);

    /* lesson_progress.lesson_id có FK REFERENCES lessons(id) nhưng KHÔNG có ON DELETE CASCADE
       (khác với lesson_resources đã có cascade) — xóa hẳn 1 lesson đã từng live (có học viên
       học rồi, sinh ra progress) mà không dọn dòng này trước sẽ vỡ foreign key constraint,
       Postgres trả lỗi, Spring bọc thành 500 chung chung không rõ nguyên nhân.
       Dùng bulk JPQL @Modifying (không phải derived "deleteBy" entity-based) để đảm bảo LUÔN
       sinh ra một câu DELETE thực thi ngay — derived delete-by trong Spring Data JPA vẫn đi qua
       persistence context (find rồi remove()), và trên thực tế đã quan sát thấy có trường hợp
       KHÔNG sinh ra câu DELETE nào dù entity match được tìm thấy (xem AiSourceRepository). */
    @Modifying
    @Query("DELETE FROM LessonProgressEntity p WHERE p.lessonId IN :lessonIds")
    void deleteByLessonIdIn(@Param("lessonIds") List<UUID> lessonIds);
}
