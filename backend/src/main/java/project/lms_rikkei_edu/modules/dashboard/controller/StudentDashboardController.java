package project.lms_rikkei_edu.modules.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.dashboard.dto.response.*;
import project.lms_rikkei_edu.modules.dashboard.service.StudentDashboardService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/student/dashboard")
@RequiredArgsConstructor
public class StudentDashboardController {

    private final StudentDashboardService studentDashboardService;
    private final CurrentUserProvider currentUserProvider;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    @GetMapping
    public ResponseEntity<StudentDashboardResponse> getDashboard() {
        return ResponseEntity.ok(studentDashboardService.getStudentDashboard(currentUserId()));
    }

    @GetMapping("/stats")
    public ResponseEntity<StudentDashboardStatsResponse> getStats() {
        return ResponseEntity.ok(studentDashboardService.getStats(currentUserId()));
    }

    @GetMapping("/in-progress-courses")
    public ResponseEntity<List<StudentDashboardResponse.CourseSummaryDto>> getInProgressCourses() {
        return ResponseEntity.ok(studentDashboardService.getInProgressCourses(currentUserId()));
    }

    @GetMapping("/due-assignments")
    public ResponseEntity<List<StudentDashboardResponse.DueAssignmentDto>> getDueAssignments() {
        return ResponseEntity.ok(studentDashboardService.getDueAssignments(currentUserId()));
    }

    @GetMapping("/due-quizzes")
    public ResponseEntity<List<StudentDashboardResponse.DueAssignmentDto>> getDueQuizzes() {
        return ResponseEntity.ok(studentDashboardService.getDueQuizzes(currentUserId()));
    }

    @GetMapping("/weekly-study-hours")
    public ResponseEntity<List<Double>> getWeeklyStudyHours() {
        return ResponseEntity.ok(studentDashboardService.getWeeklyStudyHours(currentUserId()));
    }

    @GetMapping("/skill-progress")
    public ResponseEntity<List<StudentDashboardResponse.SkillProgressDto>> getSkillProgress() {
        return ResponseEntity.ok(studentDashboardService.getSkillProgress(currentUserId()));
    }
}
