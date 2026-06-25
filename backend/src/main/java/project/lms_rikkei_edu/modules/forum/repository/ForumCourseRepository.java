package project.lms_rikkei_edu.modules.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.forum.entity.ForumCourseEntity;

import java.util.UUID;
import java.util.List;

public interface ForumCourseRepository extends JpaRepository<ForumCourseEntity, UUID> {

    @Query("select c from ForumCourseEntity c order by c.title asc")
    List<ForumCourseEntity> findAllForForum();

    @Query("select c from ForumCourseEntity c where c.instructorId = :userId order by c.title asc")
    List<ForumCourseEntity> findInstructorForumCourses(@Param("userId") UUID userId);

    @Query("""
            select c from ForumCourseEntity c
            where exists (select 1 from ForumCourseEnrollmentEntity ce where ce.courseId = c.id and ce.studentId = :userId)
            order by c.title asc
            """)
    List<ForumCourseEntity> findStudentForumCourses(@Param("userId") UUID userId);

    @Query("select count(c) > 0 from ForumCourseEntity c where c.id = :courseId and c.instructorId = :userId")
    boolean isInstructorOfCourse(@Param("courseId") UUID courseId, @Param("userId") UUID userId);

    @Query(value = "select exists(select 1 from course_enrollments where course_id = :courseId and student_id = :userId)", nativeQuery = true)
    boolean isStudentEnrolled(@Param("courseId") UUID courseId, @Param("userId") UUID userId);
}
