package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.course.entity.VideoUploadJob;
import project.lms_rikkei_edu.modules.course.enums.UploadStatus;

import java.util.Optional;
import java.util.UUID;

public interface VideoUploadJobRepository extends JpaRepository<VideoUploadJob, UUID> {

    Optional<VideoUploadJob> findTopByLessonIdAndUploadStatusOrderByCreatedAtDesc(UUID lessonId, UploadStatus status);
}
