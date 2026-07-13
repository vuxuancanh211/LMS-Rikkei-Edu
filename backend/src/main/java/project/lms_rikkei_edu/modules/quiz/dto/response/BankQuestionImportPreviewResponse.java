package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BankQuestionImportPreviewResponse {
    private String token;
    private int totalRows;
    private int newCount;
    private int duplicateCount;
    private int errorCount;
    private List<BankQuestionImportRowResult> rows;
}
