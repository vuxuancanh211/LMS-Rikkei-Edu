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
public class PendingApprovalDto {
    private UUID id;
    private String courseName;
    private String instructorName;
    private String submittedDate;
    private String status;
}
