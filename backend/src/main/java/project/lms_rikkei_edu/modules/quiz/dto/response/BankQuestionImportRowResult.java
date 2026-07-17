package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Builder
public class BankQuestionImportRowResult {
    private int rowNumber;
    private String questionText;
    private QuestionType questionType;
    private QuestionDifficulty difficulty;
    private String subjectTag;
    private List<ImportOptionDto> options;

    // NEW | DUPLICATE | ERROR
    private String status;
    private List<String> errors;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportOptionDto {
        private String text;
        private boolean correct;
        private int orderIndex;
    }
}
