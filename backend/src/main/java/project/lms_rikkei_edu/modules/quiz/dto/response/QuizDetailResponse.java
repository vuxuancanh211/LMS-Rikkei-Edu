package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuizType;
import project.lms_rikkei_edu.modules.quiz.enums.RandomMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class QuizDetailResponse {
    private UUID id;
    private UUID courseId;
    private String title;
    private String description;
    private QuizType quizType;
    private QuizStatus status;
    private Integer durationMinutes;
    private Integer maxAttempts;
    private BigDecimal passScore;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private Boolean proctoringEnabled;
    private Integer cooldownMinutes;
    private OffsetDateTime endDate;
    private OffsetDateTime publishedAt;
    private OffsetDateTime archivedAt;

    // Random Draw config
    private RandomMode randomMode;
    private Integer randomTotalCount;
    private Map<String, Integer> difficultyConfig;
    private String subjectTagFilter;

    private List<QuizQuestionResponse> questions;
}
