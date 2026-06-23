package project.lms_rikkei_edu.modules.forum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "forum_posts")
public class ForumPostEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private ForumCourseEntity course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @Column(length = 30)
    private String topic;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "is_pinned")
    private Boolean pinned;

    @Column(name = "reply_count")
    private Integer replyCount;

    @Column(name = "upvote_count")
    private Integer upvoteCount;

    @Column(name = "is_deleted")
    private Boolean deleted;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
