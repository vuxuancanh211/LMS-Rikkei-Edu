package project.lms_rikkei_edu.modules.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    /**
     * Send a message in a course-aware RAG conversation.
     * Omit {@code conversationId} to start a new conversation.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        return ResponseEntity.ok(ragChatService.chat(req));
    }

    /** List conversations for a user (student or instructor), optionally filtered by course. */
    @GetMapping("/conversations")
    public ResponseEntity<List<AiConversation>> listConversations(
            @RequestParam UUID userId,
            @RequestParam(required = false) UUID courseId
    ) {
        List<AiConversation> list = (courseId != null)
                ? conversationRepo.findByStudentIdAndCourseIdOrderByLastMessageAtDesc(userId, courseId)
                : conversationRepo.findByStudentIdOrderByLastMessageAtDesc(userId);
        return ResponseEntity.ok(list);
    }

    /** Get all messages in a conversation, oldest first. */
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<AiMessage>> getMessages(@PathVariable UUID id) {
        return ResponseEntity.ok(messageRepo.findByConversationIdOrderByCreatedAtAsc(id));
    }
}
