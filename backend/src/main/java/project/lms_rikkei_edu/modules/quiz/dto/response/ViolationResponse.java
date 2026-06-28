package project.lms_rikkei_edu.modules.quiz.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.quiz.enums.ViolationType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ViolationResponse {
    private UUID id;
    private UUID attemptId;
    private ViolationType violationType;
    private int violationOrder;
    private int totalViolations;
    private int maxViolations;
    private String actionTaken; // WARNED | AUTO_SUBMITTED
    private boolean lockedOut;
    private OffsetDateTime serverTimestamp;
}
