package project.lms_rikkei_edu.modules.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.ai.dto.request.ChatRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.ChatResponse;
import project.lms_rikkei_edu.modules.ai.entity.AiConversation;
import project.lms_rikkei_edu.modules.ai.entity.AiMessage;
import project.lms_rikkei_edu.modules.ai.repository.AiConversationRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageRepository;
import project.lms_rikkei_edu.modules.ai.service.chat.RagChatService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final RagChatService             ragChatService;
    private final AiConversationRepository   conversationRepo;
    private final AiMessageRepository        messageRepo;
    private final CurrentUserProvider        currentUserProvider;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    /**
     * Send a message in a course-aware RAG conversation.
     * Omit {@code conversationId} to start a new conversation.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        ChatRequest safeReq = new ChatRequest(
                currentUserId(), req.courseId(), req.conversationId(), req.lessonId(), req.message());
        return ResponseEntity.ok(ragChatService.chat(safeReq));
    }

    /** List conversations for the current user, optionally filtered by course. */
    @GetMapping("/conversations")
    public ResponseEntity<List<AiConversation>> listConversations(
            @RequestParam(required = false) UUID courseId
    ) {
        UUID userId = currentUserId();
        List<AiConversation> list = (courseId != null)
                ? conversationRepo.findByStudentIdAndCourseIdOrderByLastMessageAtDesc(userId, courseId)
                : conversationRepo.findByStudentIdOrderByLastMessageAtDesc(userId);
        return ResponseEntity.ok(list);
    }

    /** Get all messages in a conversation the current user owns, oldest first. */
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<AiMessage>> getMessages(@PathVariable UUID id) {
        AiConversation conversation = conversationRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Conversation not found", HttpStatus.NOT_FOUND));
        if (!conversation.getStudentId().equals(currentUserId())) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(messageRepo.findByConversationIdOrderByCreatedAtAsc(id));
    }
}
