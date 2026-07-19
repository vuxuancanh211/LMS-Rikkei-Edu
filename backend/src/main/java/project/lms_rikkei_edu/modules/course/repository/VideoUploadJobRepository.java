package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.course.entity.VideoUploadJob;
import project.lms_rikkei_edu.modules.course.enums.UploadStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoUploadJobRepository extends JpaRepository<VideoUploadJob, UUID> {

    Optional<VideoUploadJob> findTopByLessonIdAndUploadStatusOrderByCreatedAtDesc(UUID lessonId, UploadStatus status);

    /* video_upload_jobs.lesson_id có FK REFERENCES lessons(id) không có ON DELETE CASCADE —
       xem ghi chú ở LessonProgressRepository.deleteByLessonIdIn (dùng bulk @Modifying vì lý do
       tương tự — derived delete-by entity-based không đáng tin cậy để đảm bảo DELETE thực thi). */
    @Modifying
    @Query("DELETE FROM VideoUploadJob v WHERE v.lessonId IN :lessonIds")
    void deleteByLessonIdIn(@Param("lessonIds") List<UUID> lessonIds);
}
