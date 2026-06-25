package project.lms_rikkei_edu.modules.forum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "forum_reactions")
public class ForumReactionEntity {

    @Id
    private UUID id;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "reply_id")
    private UUID replyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
