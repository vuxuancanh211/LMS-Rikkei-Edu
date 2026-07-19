package project.lms_rikkei_edu.modules.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.forum.entity.ForumReplyEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumReplyRepository extends JpaRepository<ForumReplyEntity, UUID> {

    @Query("""
            select r from ForumReplyEntity r
            join fetch r.author a
            join fetch r.post p
            join fetch r.course c
            left join fetch r.parentReply
            where r.post.id = :postId and r.deleted = false
            order by r.createdAt asc
            """)
    List<ForumReplyEntity> findActiveByPostId(@Param("postId") UUID postId);

    @Query("""
            select r from ForumReplyEntity r
            join fetch r.post p
            join fetch r.course c
            join fetch r.author a
            left join fetch r.parentReply
            where r.id = :replyId and r.deleted = false
            """)
    Optional<ForumReplyEntity> findActiveById(@Param("replyId") UUID replyId);

    @Query("""
            select r from ForumReplyEntity r
            join fetch r.post p
            join fetch r.course c
            join fetch r.author a
            where r.id in :ids
            """)
    List<ForumReplyEntity> findByIdInWithRelations(@Param("ids") Collection<UUID> ids);
}
