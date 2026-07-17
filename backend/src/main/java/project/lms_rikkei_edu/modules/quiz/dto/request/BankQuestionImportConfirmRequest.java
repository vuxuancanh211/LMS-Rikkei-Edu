package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BankQuestionImportConfirmRequest {

    @NotBlank(message = "Token preview không được để trống")
    private String token;

    // Danh sách rowNumber (trong số NEW/DUPLICATE) mà giảng viên chọn để import
    private List<Integer> selectedRows;
}
