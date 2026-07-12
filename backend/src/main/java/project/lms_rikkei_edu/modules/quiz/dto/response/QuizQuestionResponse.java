package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.List;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class QuizQuestionResponse {
    private UUID id;
    private UUID bankQuestionId; // null nếu câu thủ công
    private String questionText;
    private QuestionType questionType;
    private QuestionDifficulty difficulty;
    private String subjectTag;
    private Integer orderIndex;
    private String explanation; // chỉ trả về khi được phép xem kết quả
    private List<QuizOptionResponse> options;
}
