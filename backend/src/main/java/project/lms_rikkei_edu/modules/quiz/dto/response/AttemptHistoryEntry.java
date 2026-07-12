package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AttemptHistoryEntry {
    private UUID attemptId;
    private int attemptNumber;
    private AttemptStatus status;
    private BigDecimal score;
    private BigDecimal scorePercentage;
    private Boolean isPassed;
    private int correctCount;
    private int incorrectCount;
    private int unansweredCount;
    private Integer timeSpentSeconds;
    private boolean autoSubmitted;
    private int violationCount;
    private OffsetDateTime startedAt;
    private OffsetDateTime submittedAt;
}
