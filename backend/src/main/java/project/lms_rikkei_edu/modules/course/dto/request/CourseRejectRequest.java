package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CourseRejectRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 1000)
    private String reason;
}
