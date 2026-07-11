package project.lms_rikkei_edu.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstructorDashboardResponse {
    private int activeCoursesCount;
    private int pendingCoursesCount;
    private int totalStudentsCount;
    private int totalGroupsCount;
    private int pendingSubmissionsCount;
    private List<Double> monthlyCompletionRates;
    private List<String> monthlyLabels;
    private double averageCompletionRate;
    private List<CourseDistributionDto> courseDistributions;
    private List<PendingSubmissionDto> pendingSubmissions;
}
