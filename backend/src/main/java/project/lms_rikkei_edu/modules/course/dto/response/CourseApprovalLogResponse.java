package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CourseApprovalLogResponse {
    private UUID id;
    private String action;
    private String reason;
    private Instant createdAt;
    /** ADMIN hoặc INSTRUCTOR */
    private String actorType;
    /** JSON snapshot — chỉ có khi action = SUBMITTED_* */
    private String snapshot;
}
