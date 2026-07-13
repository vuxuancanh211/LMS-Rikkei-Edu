package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class QuizAddBankQuestionsRequest {

    @NotEmpty(message = "Phải chọn ít nhất 1 câu hỏi từ ngân hàng")
    private List<UUID> bankQuestionIds;
}
