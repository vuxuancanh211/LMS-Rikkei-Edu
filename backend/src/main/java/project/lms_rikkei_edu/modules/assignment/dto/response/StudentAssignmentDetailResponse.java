package project.lms_rikkei_edu.modules.assignment.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StudentAssignmentDetailResponse {

    private UUID id;
    private UUID courseId;
    private String title;
    private String description;
    private AssignmentStatus status;
    private AssignmentScope scope;
    private OffsetDateTime deadline;
    private OffsetDateTime startDate;
    private Boolean allowLateSubmission;
    private Integer latePenaltyPercent;
    private BigDecimal maxScore;
    private BigDecimal passScore;
    private Integer maxFileSizeMb;
    private List<String> allowedFileTypes;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String courseTitle;
    private List<AssignmentAttachmentResponse> attachments;

    private SubmissionResponse studentSubmission;
}
