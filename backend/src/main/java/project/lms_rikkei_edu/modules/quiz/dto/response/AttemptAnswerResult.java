package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AttemptAnswerResult {
    private UUID questionId;
    private String questionText;
    // Toàn bộ option của câu hỏi (id + text) — để FE hiện lại đáp án đúng/sai sau khi chấm,
    // không chỉ mỗi ID trần không có nghĩa.
    private List<QuizOptionResponse> options;
    private List<UUID> selectedOptionIds;
    private List<UUID> correctOptionIds;
    // Boolean (wrapper) — primitive boolean sẽ khiến Jackson serialize field "isCorrect" thành
    // "correct" (bỏ tiền tố "is" theo JavaBean convention), làm FE luôn đọc undefined.
    private Boolean isCorrect;
}
