package project.lms_rikkei_edu.modules.quiz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.security.CourseOwnershipGuard;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.AiGenerateQuestionsRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerationJobStartResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerationJobStatusResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionResponse;
import project.lms_rikkei_edu.modules.quiz.service.AiQuestionGeneratorService;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionService;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints cho tính năng sinh câu hỏi trắc nghiệm bằng AI.
 *
 * <p>Luồng thông thường:
 * <ol>
 *   <li>POST /generate — sinh câu, kiểm tra trùng, trả về preview</li>
 *   <li>Instructor xem, bỏ chọn câu trùng / không muốn</li>
 *   <li>POST /save — lưu các câu được chọn vào bank</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/courses/{courseId}/bank-questions/ai")
@PreAuthorize("hasRole('INSTRUCTOR')")
@RequiredArgsConstructor
public class AiQuestionController {

    private final AiQuestionGeneratorService aiGeneratorService;
    private final BankQuestionService bankQuestionService;
    private final CurrentUserProvider currentUserProvider;
    private final CourseOwnershipGuard ownershipGuard;

    /**
     * Bắt đầu sinh câu hỏi bằng AI — trả về jobId ngay, pipeline thật (RAG + gọi LLM +
     * kiểm tra trùng lặp) chạy nền vì có thể mất 30-90s tuỳ số câu yêu cầu.
     * FE poll {@code GET /generate/{jobId}} để biết tiến trình và lấy kết quả khi xong.
     */
    @PostMapping("/generate")
    public ResponseEntity<AiGenerationJobStartResponse> generate(
            @PathVariable UUID courseId,
            @Valid @RequestBody AiGenerateQuestionsRequest request
    ) {
        ownershipGuard.requireOwnership(courseId);
        UUID instructorId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Chưa xác thực"));
        UUID jobId = aiGeneratorService.startGenerate(courseId, request, instructorId);
        // Cross-bean call (controller → service) — bắt buộc để @Async trên generateAsync có hiệu lực.
        aiGeneratorService.generateAsync(jobId, courseId, request);
        return ResponseEntity.ok(new AiGenerationJobStartResponse(jobId));
    }

    /** Poll tiến trình 1 job sinh câu hỏi AI — trả kèm kết quả khi step=DONE. */
    @GetMapping("/generate/{jobId}")
    public ResponseEntity<AiGenerationJobStatusResponse> generateStatus(
            @PathVariable UUID courseId,
            @PathVariable UUID jobId
    ) {
        ownershipGuard.requireOwnership(courseId);
        return ResponseEntity.ok(aiGeneratorService.getJobStatus(jobId));
    }

    /**
     * Lưu các câu hỏi đã được instructor chọn (sau khi review preview) vào bank.
     * Dùng lại endpoint createBankQuestion của BankQuestionService.
     */
    @PostMapping("/save")
    public ResponseEntity<List<BankQuestionResponse>> save(
            @PathVariable UUID courseId,
            @Valid @RequestBody List<BankQuestionRequest> questions
    ) {
        ownershipGuard.requireOwnership(courseId);
        UUID instructorId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Chưa xác thực"));
        List<BankQuestionResponse> saved = questions.stream()
                .map(q -> bankQuestionService.create(courseId, instructorId, q))
                .toList();
        return ResponseEntity.ok(saved);
    }
}
