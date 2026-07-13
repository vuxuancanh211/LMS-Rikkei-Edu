package project.lms_rikkei_edu.modules.assignment.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class InstructorSubmissionResponse {

    private UUID id;
    private int submissionNumber;
    private String status;
    private String note;
    private boolean isLate;
    private BigDecimal score;
    private String feedback;
    private OffsetDateTime submittedAt;
    private OffsetDateTime gradedAt;
    private OffsetDateTime scorePublishedAt;
    private List<SubmissionFileResponse> files;

    private UUID studentId;
    private String studentName;
    private String studentEmail;

    private UUID assignmentId;
    private String assignmentTitle;
    private BigDecimal assignmentMaxScore;
    private Integer assignmentMaxSubmissions;

    private UUID courseId;
    private String courseTitle;

    private UUID groupId;
    private String groupName;
}
