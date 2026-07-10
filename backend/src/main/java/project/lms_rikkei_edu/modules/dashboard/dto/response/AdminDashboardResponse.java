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
public class AdminDashboardResponse {
    private int totalStudentsCount;
    private int totalInstructorsCount;
    private int activeCoursesCount;
    private double averageCompletionRate;

    private List<Double> trafficData;
    private List<String> trafficLabels;

    private List<Integer> newCoursesData;
    private List<String> newCoursesLabels;

    private List<Double> weeklyTrafficData;
    private List<String> weeklyTrafficLabels;

    private List<Integer> weeklyCoursesData;
    private List<String> weeklyCoursesLabels;

    private List<PendingApprovalDto> pendingApprovals;
    private List<SystemActivityDto> recentActivities;
}
