package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AttemptAnswerResult {
    private UUID questionId;
    private String questionText;
    private List<UUID> selectedOptionIds;
    // Boolean (wrapper) — primitive boolean sẽ khiến Jackson serialize field "isCorrect" thành
    // "correct" (bỏ tiền tố "is" theo JavaBean convention), làm FE luôn đọc undefined.
    private Boolean isCorrect;
    private BigDecimal pointsEarned;
}
