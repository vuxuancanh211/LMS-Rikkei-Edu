package project.lms_rikkei_edu.modules.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.dashboard.dto.response.*;
import project.lms_rikkei_edu.modules.dashboard.service.InstructorDashboardService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instructor/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public class InstructorDashboardController {

    private final InstructorDashboardService instructorDashboardService;
    private final CurrentUserProvider currentUserProvider;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));
    }

    @GetMapping
    public ResponseEntity<InstructorDashboardResponse> getDashboard() {
        return ResponseEntity.ok(instructorDashboardService.getDashboard(currentUserId()));
    }

    @GetMapping("/stats")
    public ResponseEntity<InstructorDashboardStatsResponse> getStats() {
        return ResponseEntity.ok(instructorDashboardService.getStats(currentUserId()));
    }

    @GetMapping("/completion-chart")
    public ResponseEntity<InstructorDashboardChartResponse> getCompletionChart() {
        return ResponseEntity.ok(instructorDashboardService.getCompletionChart(currentUserId()));
    }

    @GetMapping("/course-distributions")
    public ResponseEntity<InstructorDashboardDistributionsResponse> getCourseDistributions() {
        return ResponseEntity.ok(instructorDashboardService.getCourseDistributions(currentUserId()));
    }

    @GetMapping("/pending-submissions")
    public ResponseEntity<List<PendingSubmissionDto>> getPendingSubmissions() {
        return ResponseEntity.ok(instructorDashboardService.getPendingSubmissions(currentUserId()));
    }
}
