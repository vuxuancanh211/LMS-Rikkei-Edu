package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminForumPostResponse {
    private UUID id;
    private UUID courseId;
    private String courseTitle;
    private ForumAuthorResponse author;
    private String topic;
    private String title;
    private String contentPreview;
    private boolean pinned;
    private int replyCount;
    private int upvoteCount;
    private boolean deleted;
    private int reportCount;
    private int pendingReportCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
