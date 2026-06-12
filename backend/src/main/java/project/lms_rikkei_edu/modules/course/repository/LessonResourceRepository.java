package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;

import java.util.List;
import java.util.UUID;

public interface LessonResourceRepository extends JpaRepository<LessonResource, UUID> {

    List<LessonResource> findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(UUID lessonId);
}
