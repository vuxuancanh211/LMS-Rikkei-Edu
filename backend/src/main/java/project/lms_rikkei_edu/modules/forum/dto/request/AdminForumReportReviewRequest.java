package project.lms_rikkei_edu.modules.forum.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminForumReportReviewRequest {
    @NotBlank
    private String status;

    private boolean deleteTarget;
}
