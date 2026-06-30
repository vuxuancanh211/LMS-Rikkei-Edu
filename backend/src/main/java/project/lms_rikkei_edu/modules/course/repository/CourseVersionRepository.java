package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.lms_rikkei_edu.modules.course.entity.CourseVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseVersionRepository extends JpaRepository<CourseVersion, UUID> {

    List<CourseVersion> findByCourseIdOrderByVersionNumberDesc(UUID courseId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM CourseVersion v WHERE v.courseId = :courseId")
    int findMaxVersionNumberByCourseId(UUID courseId);

    Optional<CourseVersion> findFirstByCourseIdAndStatusOrderByVersionNumberDesc(UUID courseId, String status);

    Optional<CourseVersion> findFirstByCourseIdAndStatus(UUID courseId, String status);

    List<CourseVersion> findByCourseIdAndStatusOrderBySubmittedAtDesc(UUID courseId, String status);

    long countByCourseIdAndStatus(UUID courseId, String status);

    void deleteByCourseIdAndStatus(UUID courseId, String status);
}
