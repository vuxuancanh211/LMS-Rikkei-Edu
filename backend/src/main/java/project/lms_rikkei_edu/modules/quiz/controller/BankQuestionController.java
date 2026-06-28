package project.lms_rikkei_edu.modules.quiz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionImportConfirmRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/bank-questions")
@RequiredArgsConstructor
public class BankQuestionController {

    private final BankQuestionService bankQuestionService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<BankQuestionResponse>> list(
            @PathVariable UUID courseId,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(required = false) String subjectTag) {
        return ResponseEntity.ok(bankQuestionService.list(courseId, status, difficulty, subjectTag));
    }

    @GetMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<BankQuestionResponse> getById(
            @PathVariable UUID courseId, @PathVariable UUID questionId) {
        return ResponseEntity.ok(bankQuestionService.getById(courseId, questionId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<BankQuestionResponse> create(
            @PathVariable UUID courseId,
            @Valid @RequestBody BankQuestionRequest request) {
        UUID instructorId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Không xác định được người dùng"));
        return ResponseEntity.ok(bankQuestionService.create(courseId, instructorId, request));
    }

    @PutMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<BankQuestionResponse> update(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId,
            @Valid @RequestBody BankQuestionRequest request) {
        return ResponseEntity.ok(bankQuestionService.update(courseId, questionId, request));
    }

    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID courseId, @PathVariable UUID questionId) {
        bankQuestionService.delete(courseId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{questionId}/toggle-status")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> toggleStatus(
            @PathVariable UUID courseId, @PathVariable UUID questionId) {
        bankQuestionService.toggleStatus(courseId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import/preview")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<BankQuestionImportPreviewResponse> importPreview(
            @PathVariable UUID courseId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(bankQuestionService.importPreview(courseId, file));
    }

    @PostMapping("/import/confirm")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<BankQuestionImportConfirmResponse> importConfirm(
            @PathVariable UUID courseId,
            @Valid @RequestBody BankQuestionImportConfirmRequest request) {
        UUID instructorId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Không xác định được người dùng"));
        return ResponseEntity.ok(bankQuestionService.importConfirm(courseId, instructorId, request));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "xlsx") String format) {
        byte[] data = bankQuestionService.export(courseId, format);
        boolean isCsv = "csv".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=question-bank." + (isCsv ? "csv" : "xlsx"))
                .contentType(isCsv ? MediaType.parseMediaType("text/csv")
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
