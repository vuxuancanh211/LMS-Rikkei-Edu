package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BankQuestionResponse {
    private UUID id;
    private UUID courseId;
    private String questionText;
    private QuestionType questionType;
    private QuestionDifficulty difficulty;
    private String subjectTag;
    private BigDecimal points;
    private QuestionStatus status;
    private List<BankOptionResponse> options;
    private long quizUsageCount;    // số Quiz đang dùng câu này
    private OffsetDateTime createdAt;
}
