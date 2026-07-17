package project.lms_rikkei_edu.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstructorDashboardStatsResponse {
    private int activeCoursesCount;
    private int pendingCoursesCount;
    private int totalStudentsCount;
    private int totalGroupsCount;
    private int pendingSubmissionsCount;
    private double averageCompletionRate;
}
