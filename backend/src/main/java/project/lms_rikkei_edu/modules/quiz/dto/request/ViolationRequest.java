package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.ViolationType;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ViolationRequest {

    @NotNull(message = "Loại vi phạm không được để trống")
    private ViolationType violationType;

    private String description;

    private OffsetDateTime clientTimestamp;
}
