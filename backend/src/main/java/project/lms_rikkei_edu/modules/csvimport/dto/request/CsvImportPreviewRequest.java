package project.lms_rikkei_edu.modules.csvimport.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class CsvImportPreviewRequest {

    private MultipartFile file;

    @NotBlank(message = "Vui lòng chọn vai trò mặc định")
    private String defaultRole;
}
