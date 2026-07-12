package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class BankOptionResponse {
    private UUID id;
    private String optionText;
    private Boolean isCorrect;
    private Integer orderIndex;
}
