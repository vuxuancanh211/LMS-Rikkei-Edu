package project.lms_rikkei_edu.modules.forum.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.forum.entity.ForumPostEntity;

import java.util.Optional;
import java.util.UUID;

public interface ForumPostRepository extends JpaRepository<ForumPostEntity, UUID> {

    @Query("""
            select p from ForumPostEntity p
            join fetch p.course c
            join fetch p.author a
            where p.id = :postId and p.deleted = false
            """)
    Optional<ForumPostEntity> findActiveById(@Param("postId") UUID postId);

    @Query(value = """
            select p from ForumPostEntity p
            join fetch p.course c
            join fetch p.author a
            where p.deleted = false
              and (:courseId is null or c.id = :courseId)
            order by p.pinned desc, p.createdAt desc
            """,
            countQuery = """
            select count(p) from ForumPostEntity p
            where p.deleted = false
              and (:courseId is null or p.course.id = :courseId)
            """)
    Page<ForumPostEntity> findVisibleForAdmin(@Param("courseId") UUID courseId, Pageable pageable);

    @Query(value = """
            select p from ForumPostEntity p
            join fetch p.course c
            join fetch p.author a
            where p.deleted = false
              and (:courseId is null or c.id = :courseId)
              and (lower(p.title) like lower(concat('%', :keyword, '%')) or lower(coalesce(p.content, '')) like lower(concat('%', :keyword, '%')))
            order by p.pinned desc, p.createdAt desc
            """,
            countQuery = """
            select count(p) from ForumPostEntity p
            where p.deleted = false
              and (:courseId is null or p.course.id = :courseId)
              and (lower(p.title) like lower(concat('%', :keyword, '%')) or lower(coalesce(p.content, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<ForumPostEntity> searchVisibleForAdmin(@Param("courseId") UUID courseId, @Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
            select p from ForumPostEntity p
            join fetch p.course c
            join fetch p.author a
            where p.deleted = false
              and c.instructorId = :userId
              and (:courseId is null or c.id = :courseId)
            order by p.pinned desc, p.createdAt desc
            """,
            countQuery = """
            select count(p) from ForumPostEntity p
            where p.deleted = false
              and p.course.instructorId = :userId
              and (:courseId is null or p.course.id = :courseId)
            """)
    Page<ForumPostEntity> findVisibleForInstructor(@Param("userId") UUID userId, @Param("courseId") UUID courseId, Pageable pageable);

    @Query(value = """
            select p from ForumPostEntity p
            join fetch p.course c
            join fetch p.author a
            where p.deleted = false
              and c.instructorId = :userId
              and (:courseId is null or c.id = :courseId)
              and (lower(p.title) like lower(concat('%', :keyword, '%')) or lower(coalesce(p.content, '')) like lower(concat('%', :keyword, '%')))
            order by p.pinned desc, p.createdAt desc
            """,
            countQuery = """
            select count(p) from ForumPostEntity p
            where p.deleted = false
              and p.course.instructorId = :userId
              and (:courseId is null or p.course.id = :courseId)
              and (lower(p.title) like lower(concat('%', :keyword, '%')) or lower(coalesce(p.content, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<ForumPostEntity> searchVisibleForInstructor(@Param("userId") UUID userId, @Param("courseId") UUID courseId, @Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
            select p from ForumPostEntity p
            join fetch p.course c
            join fetch p.author a
            where p.deleted = false
              and exists (select 1 from ForumCourseEnrollmentEntity ce where ce.courseId = c.id and ce.studentId = :userId)
              and (:courseId is null or c.id = :courseId)
            order by p.pinned desc, p.createdAt desc
            """,
            countQuery = """
            select count(p) from ForumPostEntity p
            where p.deleted = false
              and exists (select 1 from ForumCourseEnrollmentEntity ce where ce.courseId = p.course.id and ce.studentId = :userId)
              and (:courseId is null or p.course.id = :courseId)
            """)
    Page<ForumPostEntity> findVisibleForStudent(@Param("userId") UUID userId, @Param("courseId") UUID courseId, Pageable pageable);

    @Query(value = """
            select p from ForumPostEntity p
            join fetch p.course c
            join fetch p.author a
            where p.deleted = false
              and exists (select 1 from ForumCourseEnrollmentEntity ce where ce.courseId = c.id and ce.studentId = :userId)
              and (:courseId is null or c.id = :courseId)
              and (lower(p.title) like lower(concat('%', :keyword, '%')) or lower(coalesce(p.content, '')) like lower(concat('%', :keyword, '%')))
            order by p.pinned desc, p.createdAt desc
            """,
            countQuery = """
            select count(p) from ForumPostEntity p
            where p.deleted = false
              and exists (select 1 from ForumCourseEnrollmentEntity ce where ce.courseId = p.course.id and ce.studentId = :userId)
              and (:courseId is null or p.course.id = :courseId)
              and (lower(p.title) like lower(concat('%', :keyword, '%')) or lower(coalesce(p.content, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<ForumPostEntity> searchVisibleForStudent(@Param("userId") UUID userId, @Param("courseId") UUID courseId, @Param("keyword") String keyword, Pageable pageable);
}
