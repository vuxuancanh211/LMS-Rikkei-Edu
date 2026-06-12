package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;

@Getter
@Setter
public class ResourceConfirmUploadRequest {

    @NotBlank
    private String s3Key;

    @NotNull
    private ResourceType resourceType;

    private String displayName;

    private String originalFilename;

    private Long fileSizeBytes;

    private String mimeType;

    private Boolean isDownloadable = true;
}
