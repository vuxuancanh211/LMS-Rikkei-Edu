package project.lms_rikkei_edu.modules.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.chat.dto.request.MarkAsReadRequest;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMemberResponse;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatRoomResponse;
import project.lms_rikkei_edu.modules.chat.exception.ChatRoomNotFoundException;
import project.lms_rikkei_edu.modules.chat.service.ChatRoomService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatRoomControllerTest {

    private ChatRoomService chatRoomService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chatRoomService = mock(ChatRoomService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ChatRoomController(chatRoomService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    private void mockAuth() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(USER_ID));
    }

    // ── GET /api/chat/rooms ───────────────────────────────

    @Test
    void getMyRoomsReturns200() throws Exception {
        mockAuth();
        ChatRoomResponse room = ChatRoomResponse.builder()
                .id(UUID.randomUUID())
                .name("Room A")
                .active(true)
                .build();

        when(chatRoomService.getMyRooms(USER_ID)).thenReturn(List.of(room));

        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Room A"));
    }

    @Test
    void getMyRoomsReturnsEmptyList() throws Exception {
        mockAuth();
        when(chatRoomService.getMyRooms(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /api/chat/rooms/{roomId} ──────────────────────

    @Test
    void getRoomDetailReturns200() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();
        ChatMemberResponse member = ChatMemberResponse.builder()
                .userId(USER_ID)
                .fullName("Test User")
                .build();
        ChatRoomResponse room = ChatRoomResponse.builder()
                .id(roomId)
                .name("Room A")
                .active(true)
                .members(List.of(member))
                .build();

        when(chatRoomService.getRoomDetail(roomId, USER_ID)).thenReturn(room);

        mockMvc.perform(get("/api/chat/rooms/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId.toString()))
                .andExpect(jsonPath("$.name").value("Room A"))
                .andExpect(jsonPath("$.members", hasSize(1)));
    }

    @Test
    void getRoomDetailReturns404WhenNotFound() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();

        when(chatRoomService.getRoomDetail(roomId, USER_ID))
                .thenThrow(new ChatRoomNotFoundException(roomId));

        mockMvc.perform(get("/api/chat/rooms/{roomId}", roomId))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/chat/rooms/{roomId}/read ────────────────

    @Test
    void markAsReadReturns200() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();
        MarkAsReadRequest request = new MarkAsReadRequest(UUID.randomUUID());

        mockMvc.perform(post("/api/chat/rooms/{roomId}/read", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void markAsReadReturns404WhenNotFound() throws Exception {
        mockAuth();
        UUID roomId = UUID.randomUUID();
        MarkAsReadRequest request = new MarkAsReadRequest(UUID.randomUUID());

        when(chatRoomService.getRoomDetail(roomId, USER_ID))
                .thenThrow(new ChatRoomNotFoundException(roomId));

        mockMvc.perform(get("/api/chat/rooms/{roomId}", roomId))
                .andExpect(status().isNotFound());
    }
}
