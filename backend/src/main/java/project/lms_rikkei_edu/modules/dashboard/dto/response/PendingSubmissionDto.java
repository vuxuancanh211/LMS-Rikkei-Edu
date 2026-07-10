package project.lms_rikkei_edu.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingSubmissionDto {
    private UUID id;
    private String studentName;
    private String assignmentTitle;
    private String groupName;
    private String submittedAt;
    private String status;
}
