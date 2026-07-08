package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.List;
import java.util.UUID;

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

    /**
     * Giới hạn RAG retrieval vào các tài liệu AI này (ai_sources.id) thay vì toàn bộ
     * tài liệu đã index của khóa học — giúp sinh câu hỏi nhanh hơn khi khóa học có nhiều
     * tài liệu. Để trống/null = tìm trên toàn bộ tài liệu (hành vi mặc định trước đây).
     */
    private List<UUID> sourceIds;

    /** Số câu muốn sinh (1–40) */
    @Min(1) @Max(40)
    private int count = 5;

    /**
     * Ngưỡng cosine similarity để coi là "trùng" với câu đã có trong bank.
     * Giá trị 0–1, mặc định 0.88 (88% similarity).
     */
    private double duplicateThreshold = 0.88;
}
