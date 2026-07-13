package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class QuizQuestionStatsResponse {
    private UUID questionId;
    private String questionText;
    private QuestionType questionType;
    private QuestionDifficulty difficulty;
    private long totalAnswers;
    private long correctCount;
    private BigDecimal correctRate; // %
}
