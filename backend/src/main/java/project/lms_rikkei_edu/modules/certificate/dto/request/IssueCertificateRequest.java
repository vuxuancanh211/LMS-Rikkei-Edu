package project.lms_rikkei_edu.modules.certificate.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class IssueCertificateRequest {

    @NotNull(message = "Student id is required")
    private UUID studentId;

    @NotNull(message = "Course id is required")
    private UUID courseId;
}
