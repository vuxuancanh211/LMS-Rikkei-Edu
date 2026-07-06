package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateQuestionsResponse {

    private List<AiGeneratedQuestion> questions;

    /** Số câu sinh được (bao gồm cả trùng) */
    private int totalGenerated;

    /** Số câu bị đánh dấu trùng với bank hiện có */
    private int duplicateCount;

    /** Số câu mới (chưa có trong bank) */
    private int newCount;
}
