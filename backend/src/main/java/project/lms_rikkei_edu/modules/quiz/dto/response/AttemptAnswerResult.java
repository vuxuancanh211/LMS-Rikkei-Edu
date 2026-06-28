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
    private boolean isCorrect;
    private BigDecimal pointsEarned;
}
