package project.lms_rikkei_edu.modules.assignment.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SubmissionFileResponse {

    private UUID id;
    private String originalFilename;
    private Long fileSizeBytes;
    private String mimeType;
    private String extension;
    private String url;
}
