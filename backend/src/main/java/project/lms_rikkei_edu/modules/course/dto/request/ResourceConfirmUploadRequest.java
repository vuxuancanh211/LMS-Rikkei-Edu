package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;

@Getter
@Setter
public class ResourceConfirmUploadRequest {

    private String s3Key;

    /** Thay thế s3Key khi resource là URL ngoài (YouTube, CDN, ...) */
    private String externalUrl;

    @NotNull
    private ResourceType resourceType;

    private String displayName;

    private String originalFilename;

    private Long fileSizeBytes;

    private String mimeType;

    private Boolean isDownloadable = true;
}
