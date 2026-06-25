package project.lms_rikkei_edu.modules.forum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ForumAttachmentResponse {
    private UUID id;
    private String fileName;
    private String url;
    private String contentType;
    private long sizeBytes;
    private String attachmentType;
}
