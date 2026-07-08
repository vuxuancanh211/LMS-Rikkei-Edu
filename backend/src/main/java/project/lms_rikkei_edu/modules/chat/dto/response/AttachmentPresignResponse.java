package project.lms_rikkei_edu.modules.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class AttachmentPresignResponse {
    private String uploadUrl;
    private String viewUrl;
    private String s3Key;
}
