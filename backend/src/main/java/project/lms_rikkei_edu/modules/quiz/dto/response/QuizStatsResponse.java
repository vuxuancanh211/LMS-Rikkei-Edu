package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class QuizStatsResponse {
    private UUID quizId;
    private String quizTitle;
    private long totalAttempts;
    private long uniqueStudents;
    private BigDecimal avgScore;          // điểm trung bình
    private BigDecimal avgScorePercentage; // % trung bình
    private BigDecimal passRate;           // % học viên đạt
    private long passCount;
    private BigDecimal avgTimeSpentSeconds;
    private List<QuizQuestionStatsResponse> questionStats;
}
