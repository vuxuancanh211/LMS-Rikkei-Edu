package project.lms_rikkei_edu.modules.assignment.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateAssignmentRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(min = 5, max = 200, message = "Tiêu đề phải từ 5-200 ký tự")
    private String title;

    @Size(max = 10000, message = "Mô tả tối đa 10000 ký tự")
    private String description;

    @NotNull(message = "Scope không được để trống")
    private AssignmentScope scope;

    private List<UUID> groupIds;

    private OffsetDateTime deadline;

    private OffsetDateTime startDate;

    private Boolean allowLateSubmission;

    private Integer latePenaltyPercent;

    @DecimalMin(value = "0", inclusive = false, message = "Điểm tối đa phải lớn hơn 0")
    @DecimalMax(value = "100.00", message = "Điểm tối đa không được vượt quá 100")
    private BigDecimal maxScore;

    @DecimalMin(value = "0", message = "Điểm đạt không được nhỏ hơn 0")
    private BigDecimal passingScore;

    private Integer maxFileSizeMb;

    private List<String> allowedFileTypes;
}
