package project.lms_rikkei_edu.modules.dashboard.service;

import project.lms_rikkei_edu.modules.dashboard.dto.response.*;

import java.util.List;
import java.util.UUID;

public interface StudentDashboardService {
    StudentDashboardResponse getStudentDashboard(UUID studentId);
    StudentDashboardStatsResponse getStats(UUID studentId);
    List<StudentDashboardResponse.CourseSummaryDto> getInProgressCourses(UUID studentId);
    List<StudentDashboardResponse.DueAssignmentDto> getDueAssignments(UUID studentId);
    List<StudentDashboardResponse.DueAssignmentDto> getDueQuizzes(UUID studentId);
    List<Double> getWeeklyStudyHours(UUID studentId);
    List<StudentDashboardResponse.SkillProgressDto> getSkillProgress(UUID studentId);
}
