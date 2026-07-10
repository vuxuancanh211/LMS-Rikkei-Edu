package project.lms_rikkei_edu.modules.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.service.StudentDashboardService;

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
}
