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
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerateQuestionsResponse;
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
     * Sinh câu hỏi bằng AI và kiểm tra trùng lặp với bank hiện có.
     * Không lưu vào DB — chỉ trả về preview để instructor xem xét.
     */
    @PostMapping("/generate")
    public ResponseEntity<AiGenerateQuestionsResponse> generate(
            @PathVariable UUID courseId,
            @Valid @RequestBody AiGenerateQuestionsRequest request
    ) {
        ownershipGuard.requireOwnership(courseId);
        AiGenerateQuestionsResponse response = aiGeneratorService.generate(courseId, request);
        return ResponseEntity.ok(response);
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
