package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ForumReplyResponse {
    private UUID id;
    private UUID postId;
    private UUID courseId;
    private UUID parentReplyId;
    private ForumAuthorResponse author;
    private String content;
    private int depth;
    private List<ForumReplyResponse> replies;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
