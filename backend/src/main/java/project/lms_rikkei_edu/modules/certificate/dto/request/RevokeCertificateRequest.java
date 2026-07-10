package project.lms_rikkei_edu.modules.certificate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RevokeCertificateRequest {

    @NotBlank(message = "Revoke reason is required")
    @Size(max = 1000, message = "Revoke reason must not exceed 1000 characters")
    private String reason;
}
