package project.lms_rikkei_edu.modules.assignment.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class GradeRequest {

    @NotNull
    private UUID submissionId;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal score;

    private String feedback;
}
