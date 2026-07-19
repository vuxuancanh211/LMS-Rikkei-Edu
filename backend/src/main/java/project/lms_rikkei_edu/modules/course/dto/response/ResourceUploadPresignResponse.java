package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ResourceUploadPresignResponse {
    private String presignedUrl;
    private String s3Key;
    private String contentType;
    private Instant expiresAt;
}
