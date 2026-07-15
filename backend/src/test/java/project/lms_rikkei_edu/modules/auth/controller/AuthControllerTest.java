package project.lms_rikkei_edu.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.modules.auth.dto.request.*;
import project.lms_rikkei_edu.modules.auth.dto.response.*;
import project.lms_rikkei_edu.modules.auth.service.AuthService;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Nested
    class Login {

        @Test
        void returns200_whenCredentialsValid() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("password");

            LoginResponse resp = LoginResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .build();
            when(authService.login(any())).thenReturn(resp);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }

        @Test
        void returns400_whenEmailBlank() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("");
            req.setPassword("password");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns400_whenEmailInvalid() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("not-an-email");
            req.setPassword("password");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns401_whenServiceThrowsUnauthorized() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("wrong");

            when(authService.login(any()))
                    .thenThrow(new BusinessException("Email or password is incorrect",
                            org.springframework.http.HttpStatus.UNAUTHORIZED));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void returns429_whenRateLimited() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("password");

            when(authService.login(any()))
                    .thenThrow(new BusinessException("Too many attempts",
                            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        void usesSecretHeader_whenPasswordIsAsterisks() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("*******");

            LoginResponse resp = LoginResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .build();
            when(authService.login(any())).thenReturn(resp);

            mockMvc.perform(post("/api/auth/login")
                            .header("X-Auth-Secret", "ActualSecret123!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @Nested
    class Refresh {

        @Test
        void returns200_whenRefreshTokenValid() throws Exception {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("valid-refresh-token");

            RefreshTokenResponse resp = RefreshTokenResponse.builder()
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .build();
            when(authService.refresh(any())).thenReturn(resp);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-token"));
        }

        @Test
        void returns400_whenRefreshTokenBlank() throws Exception {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("");

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns401_whenTokenInvalid() throws Exception {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("invalid-token");

            when(authService.refresh(any()))
                    .thenThrow(new BusinessException("Refresh token is invalid or expired",
                            org.springframework.http.HttpStatus.UNAUTHORIZED));

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /api/auth/forgot-password ────────────────────────────────────────

    @Nested
    class ForgotPassword {

        @Test
        void returns200_always() throws Exception {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("test@example.com");

            when(authService.forgotPassword(any()))
                    .thenReturn(ForgotPasswordResponse.builder()
                            .message("If the email exists, a password reset link has been sent")
                            .build());

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        void returns400_whenEmailInvalid() throws Exception {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("not-an-email");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /api/auth/reset-password ─────────────────────────────────────────

    @Nested
    class ResetPassword {

        @Test
        void returns200_whenTokenValid() throws Exception {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("valid-token");
            req.setNewPassword("NewPass123!");
            req.setConfirmPassword("NewPass123!");

            when(authService.resetPassword(any()))
                    .thenReturn(ResetPasswordResponse.builder()
                            .message("Password has been reset successfully")
                            .build());

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        void returns400_whenPasswordTooShort() throws Exception {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("short");
            req.setConfirmPassword("short");

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns400_whenTokenBlank() throws Exception {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("");
            req.setNewPassword("NewPass123!");
            req.setConfirmPassword("NewPass123!");

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void usesSecretHeaders_whenPasswordsAreAsterisks() throws Exception {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("valid-token");
            req.setNewPassword("********");
            req.setConfirmPassword("********");

            when(authService.resetPassword(any()))
                    .thenReturn(ResetPasswordResponse.builder()
                            .message("Password has been reset successfully")
                            .build());

            mockMvc.perform(post("/api/auth/reset-password")
                            .header("X-Auth-Secret-New", "ActualSecret123!")
                            .header("X-Auth-Secret-Confirm", "ActualSecret123!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }

    // ── GET /api/auth/activate ────────────────────────────────────────────────

    @Nested
    class ActivateAccount {

        @Test
        void returns200_whenTokenValid() throws Exception {
            when(authService.activateAccount("valid-token"))
                    .thenReturn(ActivateAccountResponse.builder()
                            .message("Account activated successfully")
                            .build());

            mockMvc.perform(get("/api/auth/activate")
                            .param("token", "valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        void returns400_whenTokenInvalid() throws Exception {
            when(authService.activateAccount("bad-token"))
                    .thenThrow(new BusinessException("Activation token is invalid or expired"));

            mockMvc.perform(get("/api/auth/activate")
                            .param("token", "bad-token"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Nested
    class Logout {

        @Test
        void returns200_whenTokenValid() throws Exception {
            when(authService.logout(anyString()))
                    .thenReturn(LogoutResponse.builder()
                            .message("Logout successfully")
                            .build());

            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successfully"));
        }

        @Test
        void returns401_whenTokenInvalid() throws Exception {
            when(authService.logout(anyString()))
                    .thenThrow(new BusinessException("Authorization token is required",
                            org.springframework.http.HttpStatus.UNAUTHORIZED));

            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer bad-token"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
