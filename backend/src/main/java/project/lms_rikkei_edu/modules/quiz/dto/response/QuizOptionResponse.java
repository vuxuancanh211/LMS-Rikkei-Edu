package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class QuizOptionResponse {
    private UUID id;
    private String optionText;
    private Integer orderIndex;
    // is_correct KHÔNG có ở đây — không gửi về client khi đang thi
}
