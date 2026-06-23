package project.lms_rikkei_edu.modules.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.forum.entity.ForumReactionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumReactionRepository extends JpaRepository<ForumReactionEntity, UUID> {

    Optional<ForumReactionEntity> findByPostIdAndUserId(UUID postId, UUID userId);

    Optional<ForumReactionEntity> findByReplyIdAndUserId(UUID replyId, UUID userId);

    @Query("select r.postId from ForumReactionEntity r where r.postId in :postIds and r.userId = :userId")
    List<UUID> findPostIdsByPostIdInAndUserId(@Param("postIds") List<UUID> postIds, @Param("userId") UUID userId);

    @Query("select r.replyId from ForumReactionEntity r where r.replyId in :replyIds and r.userId = :userId")
    List<UUID> findReplyIdsByReplyIdInAndUserId(@Param("replyIds") List<UUID> replyIds, @Param("userId") UUID userId);
}
