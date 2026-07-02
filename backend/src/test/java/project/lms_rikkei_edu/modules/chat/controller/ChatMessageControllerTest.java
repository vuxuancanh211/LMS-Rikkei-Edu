package project.lms_rikkei_edu.modules.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.chat.dto.request.EditMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.PresignUploadRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.ReactMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.SendMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;
import project.lms_rikkei_edu.modules.chat.exception.ChatAccessDeniedException;
import project.lms_rikkei_edu.modules.chat.service.ChatMessageService;
import project.lms_rikkei_edu.modules.chat.service.ChatRoomService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatMessageControllerTest {

    private ChatMessageService chatMessageService;
    private UserRepository userRepository;
    private CurrentUserProvider currentUserProvider;
    private ChatRoomService chatRoomService;
    private S3Service s3Service;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chatMessageService = mock(ChatMessageService.class);
        userRepository = mock(UserRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        chatRoomService = mock(ChatRoomService.class);
        s3Service = mock(S3Service.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ChatMessageController(chatMessageService, userRepository,
                                currentUserProvider, chatRoomService, s3Service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    private void mockAuth() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(USER_ID));
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setFullName("Test User");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    // ── GET /api/chat/rooms/{roomId}/messages ─────────────

    @Test
    void getMessagesReturns200() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();
        ChatMessageResponse msg = ChatMessageResponse.builder()
                .id(UUID.randomUUID())
                .content("Hello")
                .senderId(USER_ID)
                .build();
        Page<ChatMessageResponse> page = new PageImpl<>(List.of(msg), PageRequest.of(0, 20), 1);

        when(chatMessageService.getMessages(eq(roomId), eq(USER_ID), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content").value("Hello"));
    }

    // ── POST /api/chat/rooms/{roomId}/messages ────────────

    @Test
    void sendMessageReturns201() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();
        SendMessageRequest request = SendMessageRequest.builder()
                .content("Hello")
                .messageType(ChatMessageEntity.MessageType.TEXT)
                .build();
        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(UUID.randomUUID())
                .content("Hello")
                .senderId(USER_ID)
                .build();

        when(chatMessageService.sendMessage(eq(roomId), any(SendMessageRequest.class), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Hello"));
    }

    @Test
    void sendMessageReturns400WhenInvalid() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();
        String invalidBody = "{\"content\": \"\", \"messageType\": \"TEXT\"}";

        when(chatMessageService.sendMessage(eq(roomId), any(SendMessageRequest.class), any(UserEntity.class)))
                .thenThrow(new BusinessException("Nội dung không được để trống"));

        mockMvc.perform(post("/api/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/chat/messages/{messageId} ────────────────

    @Test
    void editMessageReturns200() throws Exception {
        mockAuth();
        UUID messageId = UUID.randomUUID();
        EditMessageRequest request = new EditMessageRequest("Updated");
        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(messageId)
                .content("Updated")
                .edited(true)
                .build();

        when(chatMessageService.editMessage(eq(messageId), any(EditMessageRequest.class), eq(USER_ID)))
                .thenReturn(response);

        mockMvc.perform(put("/api/chat/messages/{messageId}", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated"))
                .andExpect(jsonPath("$.edited").value(true));
    }

    @Test
    void editMessageReturns403WhenNotOwner() throws Exception {
        mockAuth();
        UUID messageId = UUID.randomUUID();
        EditMessageRequest request = new EditMessageRequest("Hacked");

        when(chatMessageService.editMessage(eq(messageId), any(EditMessageRequest.class), eq(USER_ID)))
                .thenThrow(new ChatAccessDeniedException("Bạn không có quyền thực hiện thao tác này"));

        mockMvc.perform(put("/api/chat/messages/{messageId}", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/chat/messages/{messageId} ─────────────

    @Test
    void deleteMessageReturns204() throws Exception {
        mockAuth();
        UUID messageId = UUID.randomUUID();

        mockMvc.perform(delete("/api/chat/messages/{messageId}", messageId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMessageReturns403WhenNotOwner() throws Exception {
        mockAuth();
        UUID messageId = UUID.randomUUID();

        doThrow(new ChatAccessDeniedException("Bạn không có quyền thực hiện thao tác này"))
                .when(chatMessageService).deleteMessage(messageId, USER_ID);

        mockMvc.perform(delete("/api/chat/messages/{messageId}", messageId))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/chat/messages/{messageId}/reactions ─────

    @Test
    void addReactionReturns200() throws Exception {
        mockAuth();
        UUID messageId = UUID.randomUUID();
        ReactMessageRequest request = new ReactMessageRequest("👍");
        Map<String, Long> reactions = Map.of("👍", 1L);

        when(chatMessageService.addReaction(eq(messageId), any(ReactMessageRequest.class), any(UserEntity.class)))
                .thenReturn(reactions);

        mockMvc.perform(post("/api/chat/messages/{messageId}/reactions", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['👍']").value(1));
    }

    // ── DELETE /api/chat/messages/{messageId}/reactions ───

    @Test
    void removeReactionReturns200() throws Exception {
        mockAuth();
        UUID messageId = UUID.randomUUID();

        when(chatMessageService.removeReaction(messageId, "👍", USER_ID))
                .thenReturn(Map.of());

        mockMvc.perform(delete("/api/chat/messages/{messageId}/reactions", messageId)
                        .param("emoji", "👍"))
                .andExpect(status().isOk());
    }

    // ── POST /api/chat/rooms/{roomId}/attachments/presign-upload ──

    @Test
    void presignUploadReturns200() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();
        PresignUploadRequest request = new PresignUploadRequest("file.pdf", "application/pdf");

        PresignedPutObjectRequest putRequest = mock(PresignedPutObjectRequest.class);
        when(putRequest.url()).thenReturn(new URL("https://upload.example.com/file.pdf"));

        PresignedGetObjectRequest getRequest = mock(PresignedGetObjectRequest.class);
        when(getRequest.url()).thenReturn(new URL("https://view.example.com/file.pdf"));

        when(s3Service.generatePresignedPutUrl(anyString(), anyString())).thenReturn(putRequest);
        when(s3Service.generatePresignedInlineUrl(anyString(), anyLong())).thenReturn(getRequest);

        mockMvc.perform(post("/api/chat/rooms/{roomId}/attachments/presign-upload", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://upload.example.com/file.pdf"))
                .andExpect(jsonPath("$.viewUrl").value("https://view.example.com/file.pdf"))
                .andExpect(jsonPath("$.s3Key").isString());
    }

    @Test
    void presignUploadReturns400WhenInvalidExtension() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();
        PresignUploadRequest request = new PresignUploadRequest("file.exe", "application/x-msdownload");

        mockMvc.perform(post("/api/chat/rooms/{roomId}/attachments/presign-upload", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
