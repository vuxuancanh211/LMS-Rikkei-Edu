package project.lms_rikkei_edu.modules.quiz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CourseOwnershipGuard;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.*;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final CurrentUserProvider currentUserProvider;
    private final CourseOwnershipGuard ownershipGuard;

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'STUDENT')")
    public ResponseEntity<Page<QuizSummaryResponse>> list(
            @PathVariable UUID courseId,
            @RequestParam(required = false) String title,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(quizService.listByCourse(courseId, title, pageable));
    }

    @GetMapping("/{quizId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'STUDENT')")
    public ResponseEntity<QuizDetailResponse> getDetail(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        return ResponseEntity.ok(quizService.getDetail(courseId, quizId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> create(
            @PathVariable UUID courseId,
            @Valid @RequestBody QuizMetadataRequest request) {
        ownershipGuard.requireOwnership(courseId);
        UUID instructorId = resolveCurrentUser();
        return ResponseEntity.ok(quizService.create(courseId, instructorId, request));
    }

    @PutMapping("/{quizId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> updateMetadata(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizMetadataRequest request) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(quizService.updateMetadata(courseId, quizId, request));
    }

    @DeleteMapping("/{quizId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        ownershipGuard.requireOwnership(courseId);
        quizService.delete(courseId, quizId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{quizId}/questions/bank")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizDetailResponse> addBankQuestions(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizAddBankQuestionsRequest request) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(quizService.addBankQuestions(courseId, quizId, request));
    }

    @PostMapping("/{quizId}/questions/manual")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizDetailResponse> addManualQuestion(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizManualQuestionRequest request) {
        ownershipGuard.requireOwnership(courseId);
        UUID instructorId = resolveCurrentUser();
        return ResponseEntity.ok(quizService.addManualQuestion(courseId, quizId, instructorId, request));
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> removeQuestion(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID questionId) {
        ownershipGuard.requireOwnership(courseId);
        quizService.removeQuestion(courseId, quizId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{quizId}/random-config")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> configureRandomDraw(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizRandomConfigRequest request) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(quizService.configureRandomDraw(courseId, quizId, request));
    }

    @PostMapping("/{quizId}/publish")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> publish(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(quizService.publish(courseId, quizId));
    }

    @PostMapping("/{quizId}/archive")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> archive(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(quizService.archive(courseId, quizId));
    }

    @PostMapping("/{quizId}/unarchive")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> unarchive(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(quizService.unarchive(courseId, quizId));
    }

    @GetMapping("/{quizId}/dry-run")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<DryRunResponse> dryRun(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(quizService.dryRun(courseId, quizId));
    }

    @PostMapping("/{quizId}/dry-run/grade")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<DryRunGradeResponse> gradeDryRun(
            @PathVariable UUID courseId, @PathVariable UUID quizId,
            @Valid @RequestBody DryRunGradeRequest request) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(quizService.gradeDryRun(courseId, quizId, request));
    }

    private UUID resolveCurrentUser() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Không xác định được người dùng"));
    }
}
