package project.lms_rikkei_edu.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request body for obtaining a presigned S3 upload URL for a new AI knowledge-base document. */
public record SourcePresignRequest(
        @NotNull UUID courseId,
        @NotBlank String originalFilename,
        @NotBlank String mimeType
) {}
