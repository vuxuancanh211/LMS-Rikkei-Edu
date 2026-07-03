package project.lms_rikkei_edu.modules.ai.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Request to add one or more already-uploaded lesson resources to a course's AI knowledge base. */
public record AddFromResourcesRequest(
        @NotNull UUID courseId,
        @NotEmpty List<UUID> resourceIds
) {}
