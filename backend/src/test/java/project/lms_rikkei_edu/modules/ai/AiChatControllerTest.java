package project.lms_rikkei_edu.modules.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.modules.ai.controller.AiChatController;
import project.lms_rikkei_edu.modules.ai.dto.request.ChatRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.ChatResponse;
import project.lms_rikkei_edu.modules.ai.entity.AiConversation;
import project.lms_rikkei_edu.modules.ai.entity.AiMessage;
import project.lms_rikkei_edu.modules.ai.repository.AiConversationRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiMessageRepository;
import project.lms_rikkei_edu.modules.ai.service.chat.RagChatService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiChatControllerTest {

    private RagChatService ragChatService;
    private AiConversationRepository conversationRepo;
    private AiMessageRepository messageRepo;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID userId         = UUID.randomUUID();
    private final UUID courseId       = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ragChatService   = mock(RagChatService.class);
        conversationRepo = mock(AiConversationRepository.class);
        messageRepo      = mock(AiMessageRepository.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatController(ragChatService, conversationRepo, messageRepo))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── POST /api/ai/chat ─────────────────────────────────────────────────────

    @Nested
    class Chat {

        @Test
        void returns200_withAnswer() throws Exception {
            ChatRequest req = new ChatRequest(userId, courseId, null, null, "Explain recursion");

            ChatResponse resp = new ChatResponse(conversationId, UUID.randomUUID(),
                    "Recursion is...", List.of(), 150);

            when(ragChatService.chat(any())).thenReturn(resp);

            mockMvc.perform(post("/api/ai/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                    .andExpect(jsonPath("$.answer").value("Recursion is..."));
        }

        @Test
        void returns400_whenUserIdNull() throws Exception {
            String body = "{\"userId\":null,\"message\":\"Hi\"}";

            mockMvc.perform(post("/api/ai/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns400_whenMessageBlank() throws Exception {
            ChatRequest req = new ChatRequest(userId, null, null, null, "");

            mockMvc.perform(post("/api/ai/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/ai/conversations ─────────────────────────────────────────────

    @Nested
    class ListConversations {

        @Test
        void returns200_filteredByCourse() throws Exception {
            when(conversationRepo.findByStudentIdAndCourseIdOrderByLastMessageAtDesc(userId, courseId))
                    .thenReturn(List.of(new AiConversation()));

            mockMvc.perform(get("/api/ai/conversations")
                            .param("userId", userId.toString())
                            .param("courseId", courseId.toString()))
                    .andExpect(status().isOk());

            verify(conversationRepo).findByStudentIdAndCourseIdOrderByLastMessageAtDesc(userId, courseId);
        }

        @Test
        void returns200_allConversations_whenCourseIdOmitted() throws Exception {
            when(conversationRepo.findByStudentIdOrderByLastMessageAtDesc(userId))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/ai/conversations")
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk());

            verify(conversationRepo).findByStudentIdOrderByLastMessageAtDesc(userId);
        }
    }

    // ── GET /api/ai/conversations/{id}/messages ───────────────────────────────

    @Nested
    class GetMessages {

        @Test
        void returns200_withMessages() throws Exception {
            when(messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId))
                    .thenReturn(List.of(new AiMessage(), new AiMessage()));

            mockMvc.perform(get("/api/ai/conversations/{id}/messages", conversationId))
                    .andExpect(status().isOk());

            verify(messageRepo).findByConversationIdOrderByCreatedAtAsc(conversationId);
        }

        @Test
        void returns200_emptyList_whenNoMessages() throws Exception {
            when(messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/ai/conversations/{id}/messages", conversationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
