package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.GenerationStep;

/** Trạng thái hiện tại của 1 job sinh câu hỏi AI — FE poll endpoint này để biết đang ở bước nào. */
@Getter
@Builder
public class AiGenerationJobStatusResponse {
    private GenerationStep step;
    /** Chỉ có giá trị khi step = DONE. */
    private AiGenerateQuestionsResponse result;
    /** Chỉ có giá trị khi step = FAILED. */
    private String errorMessage;
}
