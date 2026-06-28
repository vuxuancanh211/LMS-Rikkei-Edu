package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BankQuestionImportConfirmResponse {
    private int totalImported;
    private int skippedCount;
}
