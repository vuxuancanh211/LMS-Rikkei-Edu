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
          and (:topic is null or p.topic = :topic)
        order by p.pinned desc, p.upvoteCount desc, p.createdAt desc
        """,
        countQuery = """
        select count(p) from ForumPostEntity p
        where p.deleted = false
          and (:courseId is null or p.course.id = :courseId)
          and (:topic is null or p.topic = :topic)
        """)
Page<ForumPostEntity> findAllActive(@Param("courseId") UUID courseId, @Param("topic") String topic, Pageable pageable);

@Query(value = """
        select p from ForumPostEntity p
        join fetch p.course c
        join fetch p.author a
        where p.deleted = false
          and (:courseId is null or c.id = :courseId)
          and (:topic is null or p.topic = :topic)
          and (lower(p.title) like lower(concat('%', :keyword, '%')) or lower(coalesce(p.content, '')) like lower(concat('%', :keyword, '%')))
        order by p.pinned desc, p.upvoteCount desc, p.createdAt desc
        """,
        countQuery = """
        select count(p) from ForumPostEntity p
        where p.deleted = false
          and (:courseId is null or p.course.id = :courseId)
          and (:topic is null or p.topic = :topic)
          and (lower(p.title) like lower(concat('%', :keyword, '%')) or lower(coalesce(p.content, '')) like lower(concat('%', :keyword, '%')))
        """)
Page<ForumPostEntity> searchActive(@Param("courseId") UUID courseId, @Param("keyword") String keyword, @Param("topic") String topic, Pageable pageable);
}
