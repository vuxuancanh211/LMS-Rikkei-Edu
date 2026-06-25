package project.lms_rikkei_edu.modules.forum.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateForumReportRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 20, message = "Reason must not exceed 20 characters")
    private String reason;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
