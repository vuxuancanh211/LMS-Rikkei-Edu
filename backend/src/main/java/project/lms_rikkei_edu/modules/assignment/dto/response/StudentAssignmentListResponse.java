package project.lms_rikkei_edu.modules.assignment.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class StudentAssignmentListResponse {

    private UUID id;
    private UUID courseId;
    private String title;
    private AssignmentStatus status;
    private OffsetDateTime deadline;
    private OffsetDateTime startDate;
    private BigDecimal maxScore;
    private BigDecimal passScore;
    private int attachmentCount;
    private String submissionStatus;
    private BigDecimal score;
}
