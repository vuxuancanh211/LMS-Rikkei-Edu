package project.lms_rikkei_edu.modules.dashboard.service;

import project.lms_rikkei_edu.modules.dashboard.dto.response.*;
import java.util.List;

public interface AdminDashboardService {
    AdminDashboardResponse getDashboard();
    AdminDashboardStatsResponse getStats();
    AdminDashboardTrafficResponse getTrafficChart();
    AdminDashboardCoursesChartResponse getCoursesChart();
    AdminDashboardUsersChartResponse getUsersChart();
    AdminDashboardEnrollmentsChartResponse getEnrollmentsChart();
    List<PendingApprovalDto> getPendingApprovals();
    List<SystemActivityDto> getRecentActivities();
}
