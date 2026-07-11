package project.lms_rikkei_edu.modules.assignment.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UpdateAssignmentRequest {

    @Size(min = 5, max = 200, message = "Tiêu đề phải từ 5-200 ký tự")
    private String title;

    @Size(max = 10000, message = "Mô tả tối đa 10000 ký tự")
    private String description;

    private AssignmentScope scope;

    private List<UUID> groupIds;

    private OffsetDateTime deadline;

    private OffsetDateTime startDate;

    private Boolean allowLateSubmission;

    private Integer latePenaltyPercent;

    private BigDecimal maxScore;

    private Integer maxFileSizeMb;

    private List<String> allowedFileTypes;

    private Integer maxSubmissions;
}
