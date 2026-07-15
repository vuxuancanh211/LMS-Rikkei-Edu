package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminForumReportResponse {
    private UUID id;
    private String targetType;
    private UUID targetId;
    private String targetTitle;
    private String targetContentPreview;
    private UUID postId;
    private String courseTitle;
    private String reason;
    private String description;
    private String status;
    private ForumAuthorResponse reporter;
    private OffsetDateTime createdAt;
    private UUID reviewedBy;
    private OffsetDateTime reviewedAt;
    private boolean targetDeleted;
}
