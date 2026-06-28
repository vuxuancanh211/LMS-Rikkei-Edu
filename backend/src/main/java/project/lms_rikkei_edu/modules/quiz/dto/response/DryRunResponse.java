package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class DryRunResponse {
    private List<QuizQuestionResponse> questions;
    private int totalQuestions;
    // Chạy thử không có đáp án thật — chỉ trả về câu hỏi để instructor preview
    private String note;
}
