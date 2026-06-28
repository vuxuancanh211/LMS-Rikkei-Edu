package project.lms_rikkei_edu.modules.quiz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.*;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'STUDENT')")
    public ResponseEntity<List<QuizSummaryResponse>> list(@PathVariable UUID courseId) {
        return ResponseEntity.ok(quizService.listByCourse(courseId));
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
        UUID instructorId = resolveCurrentUser();
        return ResponseEntity.ok(quizService.create(courseId, instructorId, request));
    }

    @PutMapping("/{quizId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> updateMetadata(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizMetadataRequest request) {
        return ResponseEntity.ok(quizService.updateMetadata(courseId, quizId, request));
    }

    @DeleteMapping("/{quizId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        quizService.delete(courseId, quizId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{quizId}/questions/bank")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizDetailResponse> addBankQuestions(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizAddBankQuestionsRequest request) {
        return ResponseEntity.ok(quizService.addBankQuestions(courseId, quizId, request));
    }

    @PostMapping("/{quizId}/questions/manual")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizDetailResponse> addManualQuestion(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizManualQuestionRequest request) {
        UUID instructorId = resolveCurrentUser();
        return ResponseEntity.ok(quizService.addManualQuestion(courseId, quizId, instructorId, request));
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> removeQuestion(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @PathVariable UUID questionId) {
        quizService.removeQuestion(courseId, quizId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{quizId}/random-config")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> configureRandomDraw(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody QuizRandomConfigRequest request) {
        return ResponseEntity.ok(quizService.configureRandomDraw(courseId, quizId, request));
    }

    @PostMapping("/{quizId}/publish")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> publish(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        return ResponseEntity.ok(quizService.publish(courseId, quizId));
    }

    @PostMapping("/{quizId}/archive")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> archive(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        return ResponseEntity.ok(quizService.archive(courseId, quizId));
    }

    @PostMapping("/{quizId}/unarchive")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSummaryResponse> unarchive(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        return ResponseEntity.ok(quizService.unarchive(courseId, quizId));
    }

    @GetMapping("/{quizId}/dry-run")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<DryRunResponse> dryRun(
            @PathVariable UUID courseId, @PathVariable UUID quizId) {
        return ResponseEntity.ok(quizService.dryRun(courseId, quizId));
    }

    private UUID resolveCurrentUser() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Không xác định được người dùng"));
    }
}
