package project.lms_rikkei_edu.modules.csvimport.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CsvImportConfirmRequest {

    @NotBlank(message = "Token không được để trống")
    private String token;

    private UUID courseId;

    private List<UUID> groupIds;
}
