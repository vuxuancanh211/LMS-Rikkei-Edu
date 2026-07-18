package project.lms_rikkei_edu.modules.assignment.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SubmissionResponse {

    private UUID id;
    private String status;
    private String note;
    private boolean isLate;
    private BigDecimal score;
    private String feedback;
    private OffsetDateTime submittedAt;
    private OffsetDateTime scorePublishedAt;
    private List<SubmissionFileResponse> files;
}
