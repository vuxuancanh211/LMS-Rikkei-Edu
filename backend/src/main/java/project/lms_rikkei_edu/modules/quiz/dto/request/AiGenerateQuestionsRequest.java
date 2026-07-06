package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

@Getter
@Setter
public class AiGenerateQuestionsRequest {

    /** Chủ đề / nội dung muốn ra câu hỏi */
    @NotBlank(message = "Chủ đề không được để trống")
    private String topic;

    @NotNull(message = "Loại câu hỏi không được để trống")
    private QuestionType questionType;

    @NotNull(message = "Độ khó không được để trống")
    private QuestionDifficulty difficulty;

    private String subjectTag;

    /** Số câu muốn sinh (1–20) */
    @Min(1) @Max(20)
    private int count = 5;

    /**
     * Ngưỡng cosine similarity để coi là "trùng" với câu đã có trong bank.
     * Giá trị 0–1, mặc định 0.88 (88% similarity).
     */
    private double duplicateThreshold = 0.88;
}
