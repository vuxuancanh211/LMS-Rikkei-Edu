package project.lms_rikkei_edu.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.dto.response.PagedResponse;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserCreateRequest;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserUpdateRequest;
import project.lms_rikkei_edu.modules.user.dto.request.ResetPasswordRequest;
import project.lms_rikkei_edu.modules.user.dto.response.AdminUserDetailResponse;
import project.lms_rikkei_edu.modules.user.dto.response.MessageResponse;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;
import project.lms_rikkei_edu.modules.user.service.UserService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIntegrationTest {

    private UserService userService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, currentUserProvider))
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    // ── GET /api/admin/users ─────────────────────────────────────────────────

    @Test
    void getUsersReturnsOk() throws Exception {
        var user = userResponse(UUID.randomUUID());
        var paged = new PagedResponse<>(List.of(user), 1, 1, 10);

        when(userService.getUsers(any())).thenReturn(paged);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value(user.getEmail()))
                .andExpect(jsonPath("$.totalRecords").value(1));
    }

    @Test
    void getUsersPassesFilters() throws Exception {
        var user = userResponse(UUID.randomUUID());
        var paged = new PagedResponse<>(List.of(user), 1, 1, 10);

        when(userService.getUsers(any())).thenReturn(paged);

        mockMvc.perform(get("/api/admin/users")
                        .param("search", "john")
                        .param("role", "ADMIN")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));

        verify(userService).getUsers(any());
    }

    // ── GET /api/admin/users/{userId} ────────────────────────────────────────

    @Test
    void getUserDetailReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        var detail = adminUserDetailResponse(userId);

        when(userService.getUserDetail(userId)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(detail.getEmail()));
    }

    // ── POST /api/admin/users ────────────────────────────────────────────────

    @Test
    void createUserReturnsOk() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var request = new AdminUserCreateRequest();
        request.setFullName("New User");
        request.setEmail("new@test.com");
        request.setRole("STUDENT");
        request.setPhoneNumber("0934567890");
        request.setCourseId(UUID.randomUUID());
        var response = userResponse(userId);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));
        when(userService.createUser(eq(adminId), any(AdminUserCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    void createUserRejectsInvalidRequest() throws Exception {
        var request = new AdminUserCreateRequest();
        request.setEmail("new@test.com");

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/admin/users/{userId} ────────────────────────────────────────

    @Test
    void updateUserReturnsOk() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var request = new AdminUserUpdateRequest();
        request.setFullName("Updated Name");
        request.setRole("INSTRUCTOR");
        var response = userResponse(userId);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));
        when(userService.updateUser(eq(adminId), eq(userId), any(AdminUserUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/admin/users/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    void updateUserRejectsInvalidRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        var request = new AdminUserUpdateRequest();
        request.setEmail("invalid-email");

        mockMvc.perform(put("/api/admin/users/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/admin/users/{userId}/reset-password ────────────────────────

    @Test
    void resetPasswordReturnsOk() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var request = new ResetPasswordRequest();
        request.setReason("User lost password");
        var response = MessageResponse.builder()
                .message("Mật khẩu mới đã được gửi tới email của người dùng")
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));
        when(userService.resetPassword(eq(adminId), eq(userId), any(ResetPasswordRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/users/{userId}/reset-password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(response.getMessage()));
    }

    @Test
    void resetPasswordUsesReasonFromRequest() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var request = new ResetPasswordRequest();
        request.setReason("Security concern");
        var response = MessageResponse.builder()
                .message("Mật khẩu mới đã được gửi tới email của người dùng")
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(adminId));
        when(userService.resetPassword(eq(adminId), eq(userId), any(ResetPasswordRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/users/{userId}/reset-password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService).resetPassword(eq(adminId), eq(userId), any(ResetPasswordRequest.class));
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private UserResponse userResponse(UUID id) {
        var resp = new UserResponse();
        resp.setId(id);
        resp.setEmail(id + "@example.com");
        resp.setFullName("Test User");
        resp.setRole("STUDENT");
        resp.setStatus("ACTIVE");
        return resp;
    }

    private AdminUserDetailResponse adminUserDetailResponse(UUID id) {
        var resp = new AdminUserDetailResponse();
        resp.setId(id);
        resp.setEmail(id + "@example.com");
        resp.setFullName("Detail User");
        resp.setRole("ADMIN");
        resp.setStatus("ACTIVE");
        resp.setCreatedAt(OffsetDateTime.now());
        resp.setUpdatedAt(OffsetDateTime.now());
        return resp;
    }
}
