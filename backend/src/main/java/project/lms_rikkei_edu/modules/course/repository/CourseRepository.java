package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    Page<Course> findAllByInstructorId(UUID instructorId, Pageable pageable);

    List<Course> findAllByInstructorId(UUID instructorId);

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

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.category WHERE c.id IN " +
           "(SELECT ce.courseId FROM CourseEnrollmentEntity ce WHERE ce.studentId = :studentId) " +
           "AND c.deletedAt IS NULL")
    List<Course> findEnrolledCoursesByStudentId(@Param("studentId") UUID studentId);

    /* Chỉ fetch tới level chapters (1 bag) — KHÔNG thêm ch.lessons/l.resources vào
       cùng query: Hibernate không cho JOIN FETCH nhiều hơn 1 collection List (bag)
       cùng lúc (MultipleBagFetchException). Lessons/resources được nạp thêm bằng
       2 query riêng trong CourseServiceImpl (cùng persistence context nên Hibernate
       tự gắn kết quả vào đúng entity Chapter/Lesson đã load ở đây). */
    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.chapters WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Course> findByIdWithFullContent(@Param("id") UUID id);

    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.chapters WHERE c.slug = :slug AND c.deletedAt IS NULL")
    Optional<Course> findBySlugWithFullContent(@Param("slug") String slug);
}
