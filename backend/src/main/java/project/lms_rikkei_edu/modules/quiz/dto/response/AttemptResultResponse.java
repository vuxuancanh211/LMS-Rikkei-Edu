package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AttemptResultResponse {
    private UUID attemptId;
    private UUID quizId;
    private AttemptStatus status;
    private BigDecimal score;
    private BigDecimal scorePercentage;
    private Boolean isPassed;
    private int correctCount;
    private int incorrectCount;
    private int unansweredCount;
    private int totalQuestions;
    private Integer timeSpentSeconds;
    private OffsetDateTime submittedAt;
    // Chi tiết từng câu — chỉ trả về nếu quiz cho phép xem kết quả
    private List<AttemptAnswerResult> answers;
}
