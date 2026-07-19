package project.lms_rikkei_edu.modules.dashboard.service;

import project.lms_rikkei_edu.modules.dashboard.dto.response.*;

import java.util.List;
import java.util.UUID;

public interface InstructorDashboardService {
    InstructorDashboardResponse getDashboard(UUID instructorId);
    InstructorDashboardStatsResponse getStats(UUID instructorId);
    InstructorDashboardChartResponse getCompletionChart(UUID instructorId);
    InstructorDashboardDistributionsResponse getCourseDistributions(UUID instructorId);
    List<PendingSubmissionDto> getPendingSubmissions(UUID instructorId);
}
