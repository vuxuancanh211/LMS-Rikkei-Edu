package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.List;

@Getter
@Setter
public class BankQuestionRequest {

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String questionText;

    @NotNull(message = "Loại câu hỏi không được để trống")
    private QuestionType questionType;

    @NotNull(message = "Độ khó không được để trống")
    private QuestionDifficulty difficulty;

    private String subjectTag;

    @NotEmpty(message = "Phải có ít nhất 2 đáp án")
    @Valid
    private List<BankOptionRequest> options;
}
