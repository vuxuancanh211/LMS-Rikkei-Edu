package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;

import java.time.Instant;
import java.util.UUID;

/* Getter+Setter+no-args ctor cần cho Jackson deserialize lại từ Redis cache "course-detail". */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
