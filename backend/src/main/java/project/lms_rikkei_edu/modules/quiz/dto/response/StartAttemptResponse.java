package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StartAttemptResponse {
    private UUID attemptId;
    private UUID quizId;
    private int attemptNumber;
    private OffsetDateTime startedAt;
    private OffsetDateTime expiresAt;
    private int durationMinutes;
    private boolean proctoringEnabled;
    // Câu hỏi trả về KHÔNG có is_correct
    private List<QuizQuestionResponse> questions;
}
