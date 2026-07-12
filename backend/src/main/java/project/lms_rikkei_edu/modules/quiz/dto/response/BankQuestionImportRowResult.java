package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.util.List;

@Getter
@Builder
public class BankQuestionImportRowResult {
    private int rowNumber;
    private String questionText;
    private QuestionType questionType;
    private QuestionDifficulty difficulty;
    private String subjectTag;

    // NEW | DUPLICATE | ERROR
    private String status;
    private List<String> errors;
}
