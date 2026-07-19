package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiGeneratedQuestion {

    private String questionText;
    private QuestionType questionType;
    private QuestionDifficulty difficulty;
    private List<AiGeneratedOption> options;

    /** true nếu câu này bị coi là trùng với câu đã có trong bank */
    private boolean duplicate;

    /** ID câu hỏi trong bank bị trùng (nếu duplicate = true) */
    private String duplicateOfId;

    /** Mức độ tương đồng với câu trùng (0–1) */
    private double similarityScore;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiGeneratedOption {
        private String text;
        private boolean correct;
        private String explanation;
    }
}
