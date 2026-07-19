package project.lms_rikkei_edu.modules.quiz.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.AutosaveRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.SubmitAttemptRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AttemptResultResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.StartAttemptResponse;
import project.lms_rikkei_edu.modules.quiz.service.QuizAttemptService;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/quizzes/{quizId}/attempts")
@RequiredArgsConstructor
public class QuizAttemptController {

    private final QuizAttemptService attemptService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StartAttemptResponse> startAttempt(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            HttpServletRequest httpRequest) {
        UUID studentId = resolveCurrentUser();
        String ip = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(attemptService.startAttempt(courseId, quizId, studentId, ip));
    }

    @PutMapping("/{attemptId}/autosave")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> autosave(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID attemptId,
            @RequestBody AutosaveRequest request) {
        UUID studentId = resolveCurrentUser();
        attemptService.autosave(attemptId, studentId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttemptResultResponse> submit(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID attemptId,
            @RequestBody SubmitAttemptRequest request) {
        UUID studentId = resolveCurrentUser();
        return ResponseEntity.ok(attemptService.submit(attemptId, studentId, request));
    }

    @GetMapping("/{attemptId}/result")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<AttemptResultResponse> getResult(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID attemptId) {
        UUID requesterId = resolveCurrentUser();
        return ResponseEntity.ok(attemptService.getResult(attemptId, requesterId));
    }

    private UUID resolveCurrentUser() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Không xác định được người dùng"));
    }
}
