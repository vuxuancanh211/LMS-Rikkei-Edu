package project.lms_rikkei_edu.modules.dashboard.service;

import project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardResponse;

import java.util.UUID;

public interface InstructorDashboardService {
    InstructorDashboardResponse getDashboard(UUID instructorId);
}
