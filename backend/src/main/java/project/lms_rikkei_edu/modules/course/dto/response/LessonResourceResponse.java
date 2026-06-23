package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class LessonResourceResponse {
    private UUID id;
    private ResourceType resourceType;
    private String displayName;
    private String originalFilename;
    private Long fileSizeBytes;
    private String mimeType;
    private Boolean isDownloadable;
    private Integer orderIndex;
    /** Có giá trị khi resource là external URL (s3Key bắt đầu bằng "ext://") */
    private String externalUrl;
    private Instant uploadedAt;
    private Boolean isNewInUpdate;
    private Boolean pendingDelete;
}
