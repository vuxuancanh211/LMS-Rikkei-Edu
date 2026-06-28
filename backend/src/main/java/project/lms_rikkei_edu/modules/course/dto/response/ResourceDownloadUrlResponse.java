package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ResourceDownloadUrlResponse {
    private String url;
    private Instant expiresAt;
}
