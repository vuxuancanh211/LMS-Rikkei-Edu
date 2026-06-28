package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    Page<Course> findAllByInstructorId(UUID instructorId, Pageable pageable);

    boolean existsByIdAndInstructorId(UUID id, UUID instructorId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    Page<Course> findAllByStatusIn(List<CourseStatus> statuses, Pageable pageable);

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.category WHERE c.id = :id")
    Optional<Course> findByIdWithCategory(@Param("id") UUID id);

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.chapters ch LEFT JOIN FETCH ch.lessons WHERE c.id = :id")
    Optional<Course> findByIdWithFullStructure(@Param("id") UUID id);

    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.chapters ch LEFT JOIN FETCH ch.lessons l LEFT JOIN FETCH l.resources WHERE c.id = :id")
    Optional<Course> findByIdWithResources(@Param("id") UUID id);

    @Query(value = "SELECT * FROM courses WHERE id = :id", nativeQuery = true)
    Optional<Course> findByIdIncludingDeleted(@Param("id") UUID id);

    @Query(value = "SELECT * FROM courses WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", nativeQuery = true)
    List<Course> findAllDeleted();

    @Query(value = "SELECT * FROM courses WHERE deleted_at IS NOT NULL AND instructor_id = :instructorId ORDER BY deleted_at DESC", nativeQuery = true)
    List<Course> findAllDeletedByInstructorId(@Param("instructorId") UUID instructorId);
}
