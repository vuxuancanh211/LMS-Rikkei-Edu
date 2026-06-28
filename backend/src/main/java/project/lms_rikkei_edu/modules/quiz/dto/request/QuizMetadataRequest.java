package project.lms_rikkei_edu.modules.quiz.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.QuizType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class QuizMetadataRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String description;

    @NotNull(message = "Loại quiz không được để trống")
    private QuizType quizType;

    @Min(value = 1, message = "Thời gian làm bài phải ít nhất 1 phút")
    private Integer durationMinutes;

    @Min(value = 1, message = "Số lần thử phải ít nhất 1")
    private Integer maxAttempts = 3;

    private BigDecimal passScore;

    private Boolean shuffleQuestions = false;

    private Boolean shuffleOptions = false;

    private Boolean proctoringEnabled = false;

    @Min(value = 0, message = "Cooldown không được âm")
    private Integer cooldownMinutes = 20;

    private OffsetDateTime endDate;
}
