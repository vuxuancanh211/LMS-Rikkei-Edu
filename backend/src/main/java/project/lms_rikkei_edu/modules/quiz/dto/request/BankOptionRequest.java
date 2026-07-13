package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BankOptionRequest {

    @NotBlank(message = "Nội dung đáp án không được để trống")
    private String optionText;

    @NotNull(message = "Phải chỉ định đáp án đúng/sai")
    private Boolean isCorrect;

    private Integer orderIndex;
}
