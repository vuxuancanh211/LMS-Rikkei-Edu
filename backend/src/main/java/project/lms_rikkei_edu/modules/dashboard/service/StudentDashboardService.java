package project.lms_rikkei_edu.modules.dashboard.service;

import project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse;

import java.util.UUID;

public interface StudentDashboardService {
    StudentDashboardResponse getStudentDashboard(UUID studentId);
}
