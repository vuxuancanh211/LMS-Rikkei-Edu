package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả chấm bản xem thử (dry run) — tính toán trực tiếp, KHÔNG lưu vào DB,
 * KHÔNG tính vào thống kê thật của quiz.
 */
@Getter
@Builder
public class DryRunGradeResponse {
    private BigDecimal score;
    private BigDecimal maxScore;
    private BigDecimal scorePercentage;
    private Boolean isPassed;
    private int correctCount;
    private int incorrectCount;
    private int unansweredCount;
    private int totalQuestions;
    private List<DryRunAnswerResult> answers;
}
