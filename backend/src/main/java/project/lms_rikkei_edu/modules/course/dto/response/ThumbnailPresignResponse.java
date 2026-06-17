package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ThumbnailPresignResponse {
    private String uploadUrl;
    private String s3Key;
    private String viewUrl;
}
