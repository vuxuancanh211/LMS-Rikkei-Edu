package project.lms_rikkei_edu.modules.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.service.InstructorDashboardService;

import java.util.UUID;

@RestController
@RequestMapping("/api/instructor/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public class InstructorDashboardController {

    private final InstructorDashboardService instructorDashboardService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<InstructorDashboardResponse> getDashboard() {
        UUID userId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));
        return ResponseEntity.ok(instructorDashboardService.getDashboard(userId));
    }
}
