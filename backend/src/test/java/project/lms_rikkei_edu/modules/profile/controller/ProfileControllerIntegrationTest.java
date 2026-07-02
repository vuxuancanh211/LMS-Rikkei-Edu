package project.lms_rikkei_edu.modules.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.profile.dto.request.ChangePasswordRequest;
import project.lms_rikkei_edu.modules.profile.dto.request.ProfileUpdateRequest;
import project.lms_rikkei_edu.modules.profile.dto.response.ProfileResponse;
import project.lms_rikkei_edu.modules.profile.service.ProfileService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfileControllerIntegrationTest {

    private ProfileService profileService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService, currentUserProvider))
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    // ── GET /api/profile ─────────────────────────────────────────────────────

    @Test
    void getProfile_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var response = profileResponse(userId);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(profileService.getProfile(userId)).thenReturn(response);

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(response.getEmail()))
                .andExpect(jsonPath("$.fullName").value(response.getFullName()));
    }

    @Test
    void getProfile_whenNotAuthenticated_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/profile ─────────────────────────────────────────────────────

    @Test
    void updateProfile_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var request = new ProfileUpdateRequest();
        request.setFullName("Updated Name");
        request.setPhoneNumber("0912345678");
        request.setGender("MALE");
        request.setBio("Hello");
        var response = profileResponse(userId);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(profileService.updateProfile(eq(userId), any(ProfileUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    // ── POST /api/profile/change-password ────────────────────────────────────

    @Test
    void changePassword_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var request = new ChangePasswordRequest();
        request.setCurrentPassword("old_pass");
        request.setNewPassword("new_pass");
        var response = profileResponse(userId);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(profileService.changePassword(eq(userId), any(ChangePasswordRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/profile/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    void changePassword_missingRequiredFields_returns400() throws Exception {
        var request = new ChangePasswordRequest();

        mockMvc.perform(post("/api/profile/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_blankFields_returns400() throws Exception {
        var request = new ChangePasswordRequest();
        request.setCurrentPassword("");
        request.setNewPassword("");

        mockMvc.perform(post("/api/profile/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/profile/avatar ─────────────────────────────────────────────

    @Test
    void uploadAvatar_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var response = profileResponse(userId);
        var file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[1024]);

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
        when(profileService.uploadAvatar(eq(userId), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/profile/avatar")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    void uploadAvatar_noFile_returns400() throws Exception {
        mockMvc.perform(multipart("/api/profile/avatar"))
                .andExpect(status().isBadRequest());
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private ProfileResponse profileResponse(UUID id) {
        var resp = new ProfileResponse();
        resp.setId(id);
        resp.setEmail(id + "@example.com");
        resp.setFullName("Test User");
        resp.setRole("STUDENT");
        resp.setStatus("ACTIVE");
        resp.setPhoneNumber("0912345678");
        resp.setBirthDate(LocalDate.of(2000, 1, 1));
        resp.setGender("MALE");
        resp.setBio("Bio");
        resp.setCreatedAt(OffsetDateTime.now());
        return resp;
    }
}
