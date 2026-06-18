package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ForumPostResponse {
    private UUID id;
    private UUID courseId;
    private String courseTitle;
    private ForumAuthorResponse author;
    private String title;
    private String content;
    private boolean pinned;
    private int replyCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
