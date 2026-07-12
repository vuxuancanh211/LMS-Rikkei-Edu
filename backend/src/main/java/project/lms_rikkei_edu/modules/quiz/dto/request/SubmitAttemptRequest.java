package project.lms_rikkei_edu.modules.quiz.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class SubmitAttemptRequest {
    // Final answers — nếu null thì dùng autosave từ Redis
    private Map<UUID, List<UUID>> answers;
}
