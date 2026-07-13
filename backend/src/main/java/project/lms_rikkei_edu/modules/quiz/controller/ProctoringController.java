package project.lms_rikkei_edu.modules.quiz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.ViolationRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.ViolationResponse;
import project.lms_rikkei_edu.modules.quiz.service.ProctoringService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attempts/{attemptId}/proctoring")
@RequiredArgsConstructor
public class ProctoringController {

    private final ProctoringService proctoringService;
    private final CurrentUserProvider currentUserProvider;

    // Student báo cáo vi phạm từ client (tab switch, window blur, v.v.)
    @PostMapping("/violations")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ViolationResponse> reportViolation(
            @PathVariable UUID attemptId,
            @Valid @RequestBody ViolationRequest request) {
        UUID studentId = resolveCurrentUser();
        return ResponseEntity.ok(proctoringService.reportViolation(attemptId, studentId, request));
    }

    // Instructor / Admin xem lịch sử vi phạm của 1 attempt
    @GetMapping("/violations")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ViolationResponse>> getViolations(@PathVariable UUID attemptId) {
        UUID requesterId = resolveCurrentUser();
        return ResponseEntity.ok(proctoringService.getViolations(attemptId, requesterId));
    }

    private UUID resolveCurrentUser() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Không xác định được người dùng"));
    }
}
