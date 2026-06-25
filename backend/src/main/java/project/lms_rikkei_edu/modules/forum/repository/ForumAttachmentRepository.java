package project.lms_rikkei_edu.modules.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.forum.entity.ForumAttachmentEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ForumAttachmentRepository extends JpaRepository<ForumAttachmentEntity, UUID> {
    List<ForumAttachmentEntity> findByPostIdInOrderByCreatedAtAsc(List<UUID> postIds);

    List<ForumAttachmentEntity> findByReplyIdInOrderByCreatedAtAsc(List<UUID> replyIds);

    List<ForumAttachmentEntity> findByIdInAndUploaderId(List<UUID> ids, UUID uploaderId);

    List<ForumAttachmentEntity> findByPostIdIsNullAndReplyIdIsNullAndCreatedAtBefore(OffsetDateTime cutoff);
}
