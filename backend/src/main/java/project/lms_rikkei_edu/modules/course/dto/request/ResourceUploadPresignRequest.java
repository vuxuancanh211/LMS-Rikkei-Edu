package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;

@Getter
@Setter
public class ResourceUploadPresignRequest {

    @NotBlank
    private String originalFilename;

    @NotBlank
    private String mimeType;

    @NotNull
    @Positive
    private Long fileSizeBytes;

    @NotNull
    private ResourceType resourceType;

    private String displayName;
}
