package project.lms_rikkei_edu.modules.csvimport.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupMemberCsvImportConfirmRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
