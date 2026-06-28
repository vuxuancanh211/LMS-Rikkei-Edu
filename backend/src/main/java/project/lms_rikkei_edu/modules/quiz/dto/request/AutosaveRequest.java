package project.lms_rikkei_edu.modules.quiz.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class AutosaveRequest {
    // questionId → list optionId đã chọn
    private Map<UUID, List<UUID>> answers;
}
