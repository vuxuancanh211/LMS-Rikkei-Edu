package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuizType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class StudentQuizProgressEntry {
    private UUID quizId;
    private String quizTitle;
    private QuizType quizType;
    private QuizStatus quizStatus;
    private int maxAttempts;
    private int attemptsUsed;
    private boolean passed;
    private BigDecimal bestScore;
    private BigDecimal bestScorePercentage;
    private boolean canRetry; // còn lượt và cooldown đã qua
}
