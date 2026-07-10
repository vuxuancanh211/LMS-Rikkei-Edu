package project.lms_rikkei_edu.modules.assignment.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AssignmentAttachmentResponse {

    private UUID id;
    private UUID assignmentId;
    private String displayName;
    private String originalFilename;
    private Long fileSizeBytes;
    private String mimeType;
    private Integer orderIndex;
    private OffsetDateTime uploadedAt;
    private String url;
}
