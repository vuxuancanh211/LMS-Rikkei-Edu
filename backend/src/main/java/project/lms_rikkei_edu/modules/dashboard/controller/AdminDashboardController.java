package project.lms_rikkei_edu.modules.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.modules.dashboard.dto.response.*;
import project.lms_rikkei_edu.modules.dashboard.service.AdminDashboardService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminDashboardService.getDashboard());
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminDashboardStatsResponse> getStats() {
        return ResponseEntity.ok(adminDashboardService.getStats());
    }

    @GetMapping("/traffic")
    public ResponseEntity<AdminDashboardTrafficResponse> getTrafficChart() {
        return ResponseEntity.ok(adminDashboardService.getTrafficChart());
    }

    @GetMapping("/courses-chart")
    public ResponseEntity<AdminDashboardCoursesChartResponse> getCoursesChart() {
        return ResponseEntity.ok(adminDashboardService.getCoursesChart());
    }

    @GetMapping("/users-chart")
    public ResponseEntity<AdminDashboardUsersChartResponse> getUsersChart() {
        return ResponseEntity.ok(adminDashboardService.getUsersChart());
    }

    @GetMapping("/enrollments-chart")
    public ResponseEntity<AdminDashboardEnrollmentsChartResponse> getEnrollmentsChart() {
        return ResponseEntity.ok(adminDashboardService.getEnrollmentsChart());
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<List<PendingApprovalDto>> getPendingApprovals() {
        return ResponseEntity.ok(adminDashboardService.getPendingApprovals());
    }
}
