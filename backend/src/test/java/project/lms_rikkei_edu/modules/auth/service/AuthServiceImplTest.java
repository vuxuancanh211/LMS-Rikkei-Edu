package project.lms_rikkei_edu.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.JwtService;
import project.lms_rikkei_edu.infrastructure.email.EmailService;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.modules.auth.dto.request.ForgotPasswordRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.LoginRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.RefreshTokenRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.ResetPasswordRequest;
import project.lms_rikkei_edu.modules.auth.dto.response.*;
import project.lms_rikkei_edu.modules.auth.service.impl.AuthServiceImpl;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.mapper.UserMapper;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserMapper userMapper;
    @Mock RedisService redisService;
    @Mock EmailService emailService;
    @Mock CurrentUserProvider currentUserProvider;

    AuthServiceImpl authService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtService,
                userMapper, redisService, emailService, currentUserProvider
        );
        ReflectionTestUtils.setField(authService, "passwordResetUrl", "https://example.com/reset-password");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserEntity activeUser() {
        UserEntity u = new UserEntity();
        u.setId(userId);
        u.setEmail("test@example.com");
        u.setPasswordHash("hashed");
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private UserResponse userResponse() {
        UserResponse r = new UserResponse();
        r.setId(userId);
        r.setEmail("test@example.com");
        return r;
    }

    // helper to compute the same SHA-256 hash the service uses
    private String hashOf(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Nested
    class Login {

        @Test
        void returnsTokens_whenCredentialsValid() {
            LoginRequest req = new LoginRequest();
            req.setEmail("Test@Example.com");
            req.setPassword("password");

            UserEntity user = activeUser();
            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
            when(jwtService.generateAccessToken(user)).thenReturn("access-token");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toResponse(user)).thenReturn(userResponse());

            LoginResponse resp = authService.login(req);

            assertThat(resp.getAccessToken()).isEqualTo("access-token");
            assertThat(resp.getRefreshToken()).isNotNull();
            assertThat(resp.getTokenType()).isEqualTo("Bearer");
            verify(redisService).saveRefreshToken(eq(userId), anyString());
        }

        @Test
        void throws401_whenUserNotFound() {
            LoginRequest req = new LoginRequest();
            req.setEmail("notfound@example.com");
            req.setPassword("password");

            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("notfound@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        void throws401_whenPasswordWrong() {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("wrong");

            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(activeUser()));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        void throws403_whenAccountNotActive() {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("password");

            UserEntity user = activeUser();
            user.setStatus(UserStatus.PENDING_ACTIVATION);

            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        void throws429_whenRateLimitExceeded() {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("password");

            when(redisService.isRateLimited(anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        }

        @Test
        void throws429_whenIpRateLimitExceeded() {
            org.springframework.mock.web.MockHttpServletRequest mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
            mockRequest.addHeader("X-Forwarded-For", "192.168.1.100");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(mockRequest));

            try {
                LoginRequest req = new LoginRequest();
                req.setEmail("test@example.com");
                req.setPassword("password");

                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + "test@example.com"))).thenReturn(false);
                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + "ip:192.168.1.100"))).thenReturn(true);

                assertThatThrownBy(() -> authService.login(req))
                        .isInstanceOf(BusinessException.class)
                        .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
            } finally {
                org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        void login_decodesBase64Password() {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword(java.util.Base64.getEncoder().encodeToString("password".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            UserEntity user = activeUser();
            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
            when(jwtService.generateAccessToken(user)).thenReturn("access-token");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toResponse(user)).thenReturn(userResponse());

            LoginResponse resp = authService.login(req);
            assertThat(resp.getAccessToken()).isEqualTo("access-token");
        }

        @Test
        void login_withXRealIp_checksIpRateLimit() {
            org.springframework.mock.web.MockHttpServletRequest mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
            mockRequest.addHeader("X-Real-IP", "10.0.0.1");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(mockRequest));

            try {
                LoginRequest req = new LoginRequest();
                req.setEmail("test@example.com");
                req.setPassword("password");

                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + "test@example.com"))).thenReturn(false);
                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + "ip:10.0.0.1"))).thenReturn(true);

                assertThatThrownBy(() -> authService.login(req))
                        .isInstanceOf(BusinessException.class)
                        .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
            } finally {
                org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        void login_withCommaSeparatedForwardedFor_takesFirstIp() {
            org.springframework.mock.web.MockHttpServletRequest mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
            mockRequest.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(mockRequest));

            try {
                LoginRequest req = new LoginRequest();
                req.setEmail("test@example.com");
                req.setPassword("password");

                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + "test@example.com"))).thenReturn(false);
                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + "ip:203.0.113.195"))).thenReturn(true);

                assertThatThrownBy(() -> authService.login(req))
                        .isInstanceOf(BusinessException.class)
                        .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
            } finally {
                org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        void login_withNullOrNonBase64Password_proceedsWithRaw() {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("!@#InvalidBase64!@#");

            UserEntity user = activeUser();
            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(eq("!@#InvalidBase64!@#"), anyString())).thenReturn(true);
            when(jwtService.generateAccessToken(user)).thenReturn("access-token");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toResponse(user)).thenReturn(userResponse());

            LoginResponse resp = authService.login(req);
            assertThat(resp.getAccessToken()).isEqualTo("access-token");
        }

        @Test
        void getClientIp_fallbackBranches() {
            org.springframework.mock.web.MockHttpServletRequest mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
            mockRequest.addHeader("X-Forwarded-For", "unknown");
            mockRequest.addHeader("X-Real-IP", "unknown");
            mockRequest.setRemoteAddr("192.168.1.100");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(mockRequest));

            try {
                LoginRequest req = new LoginRequest();
                req.setEmail("test@example.com");
                req.setPassword("password");

                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + "test@example.com"))).thenReturn(false);
                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + "ip:192.168.1.100"))).thenReturn(true);

                assertThatThrownBy(() -> authService.login(req))
                        .isInstanceOf(BusinessException.class)
                        .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
            } finally {
                org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        void login_withEmptyOrNullPassword_testsDecodeBranches() {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("");

            UserEntity user = activeUser();
            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(eq(""), anyString())).thenReturn(true);
            when(jwtService.generateAccessToken(user)).thenReturn("access-token");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toResponse(user)).thenReturn(userResponse());

            LoginResponse resp = authService.login(req);
            assertThat(resp.getAccessToken()).isEqualTo("access-token");
        }

        @Test
        void throws403_whenAccountDisabled() {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("password");

            UserEntity user = activeUser();
            user.setDisabledAt(OffsetDateTime.now());

            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ── getMe ─────────────────────────────────────────────────────────────────

    @Nested
    class GetMe {

        @Test
        void returnsUserResponse_whenAuthenticated() {
            UserEntity user = activeUser();
            when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userMapper.toResponse(user)).thenReturn(userResponse());

            UserResponse resp = authService.getMe();

            assertThat(resp.getId()).isEqualTo(userId);
        }

        @Test
        void throwsAuthException_whenNoCurrentUser() {
            when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getMe())
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        }

        @Test
        void throws404_whenUserDeleted() {
            UserEntity user = activeUser();
            user.setDeletedAt(OffsetDateTime.now());

            when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.getMe())
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        void throws403_whenUserDisabled() {
            UserEntity user = activeUser();
            user.setDisabledAt(OffsetDateTime.now());

            when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.getMe())
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Nested
    class Refresh {

        private String validRefreshToken() {
            return userId + ".someRandomPart";
        }

        @Test
        void returnsNewTokens_whenRefreshTokenValid() {
            String token = validRefreshToken();
            UserEntity user = activeUser();

            when(redisService.getRefreshToken(userId)).thenReturn(Optional.of(hashOf(token)));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken(token);

            RefreshTokenResponse resp = authService.refresh(req);

            assertThat(resp.getAccessToken()).isEqualTo("new-access-token");
            assertThat(resp.getRefreshToken()).isNotNull();
        }

        @Test
        void returnsNewTokens_whenIsRefreshTokenValidFastPath() {
            String token = validRefreshToken();
            UserEntity user = activeUser();

            when(redisService.isRefreshTokenValid(userId, hashOf(token))).thenReturn(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(jwtService.generateAccessToken(user)).thenReturn("fast-access-token");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken(token);

            RefreshTokenResponse resp = authService.refresh(req);

            assertThat(resp.getAccessToken()).isEqualTo("fast-access-token");
            verify(redisService, never()).getRefreshToken(any());
        }

        @Test
        void throws403_whenUserNotActiveInRefresh() {
            String token = validRefreshToken();
            UserEntity user = activeUser();
            user.setStatus(UserStatus.PENDING_ACTIVATION);

            when(redisService.isRefreshTokenValid(userId, hashOf(token))).thenReturn(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken(token);

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

            verify(redisService).deleteAllRefreshTokens(userId);
        }

        @Test
        void throws401_whenUserDeletedInRefresh() {
            String token = validRefreshToken();
            UserEntity user = activeUser();
            user.setDeletedAt(OffsetDateTime.now());

            when(redisService.isRefreshTokenValid(userId, hashOf(token))).thenReturn(true);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken(token);

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        void throws401_whenRefreshTokenNotInRedis() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken(userId + ".tokenPart");

            when(redisService.getRefreshToken(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        void throws401_whenRefreshTokenMismatch() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken(userId + ".tokenPart");

            when(redisService.getRefreshToken(userId)).thenReturn(Optional.of("different-hash"));

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        void throws_whenRefreshTokenMalformed() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("notauuid.part");

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void throws_whenRefreshTokenHasNoSeparator() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("notokenatall");

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BusinessException.class);
        }

    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Nested
    class Logout {

        @Test
        void blacklistsTokenAndDeletesRefreshToken() {
            String jti = "jti-123";
            Date expiry = new Date(System.currentTimeMillis() + 3600_000);

            when(jwtService.resolveToken("Bearer some-token")).thenReturn("some-token");
            when(jwtService.extractJti("some-token")).thenReturn(jti);
            when(jwtService.extractExpiration("some-token")).thenReturn(expiry);
            when(jwtService.extractUserId("some-token")).thenReturn(userId);

            LogoutResponse resp = authService.logout("Bearer some-token");

            assertThat(resp.getMessage()).contains("Logout");
            verify(redisService).blacklistAccessToken(jti, expiry);
            verify(redisService).deleteRefreshToken(userId);
        }

        @Test
        void throws401_whenNoAuthHeader() {
            when(jwtService.resolveToken(null)).thenReturn(null);

            assertThatThrownBy(() -> authService.logout(null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        void throws401_whenJtiBlank() {
            when(jwtService.resolveToken("Bearer token")).thenReturn("token");
            when(jwtService.extractJti("token")).thenReturn("");

            assertThatThrownBy(() -> authService.logout("Bearer token"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        void blacklistsTokenAndDeletesSpecificRefreshToken_whenRequestProvided() {
            String jti = "jti-456";
            Date expiry = new Date(System.currentTimeMillis() + 3600_000);

            when(jwtService.resolveToken("Bearer some-token")).thenReturn("some-token");
            when(jwtService.extractJti("some-token")).thenReturn(jti);
            when(jwtService.extractExpiration("some-token")).thenReturn(expiry);
            when(jwtService.extractUserId("some-token")).thenReturn(userId);

            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("specific-refresh-token");

            LogoutResponse resp = authService.logout("Bearer some-token", req);

            assertThat(resp.getMessage()).contains("Logout");
            verify(redisService).deleteRefreshToken(userId, hashOf("specific-refresh-token"));
        }

    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Nested
    class ForgotPassword {

        @Test
        void sendsEmail_whenUserExists() {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("test@example.com");

            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(activeUser()));

            ForgotPasswordResponse resp = authService.forgotPassword(req);

            assertThat(resp.getMessage()).contains("email exists");
            verify(emailService).sendPasswordResetMail(eq("test@example.com"), anyString());
            verify(redisService).savePasswordResetToken(anyString(), eq(userId));
        }

        @Test
        void returnsSuccess_whenUserNotFound_andDoesNotLeak() {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("unknown@example.com");

            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("unknown@example.com"))
                    .thenReturn(Optional.empty());

            ForgotPasswordResponse resp = authService.forgotPassword(req);

            // Phải trả cùng message để không leak thông tin user có tồn tại không
            assertThat(resp.getMessage()).contains("email exists");
            verify(emailService, never()).sendPasswordResetMail(any(), any());
        }

        @Test
        void returnsSuccess_whenRateLimited() {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("test@example.com");

            when(redisService.isRateLimited(anyString())).thenReturn(true);

            ForgotPasswordResponse resp = authService.forgotPassword(req);

            assertThat(resp.getMessage()).isNotNull();
            verify(userRepository, never()).findByEmailIgnoreCaseAndDeletedAtIsNull(any());
        }

        @Test
        void returnsSuccess_whenIpRateLimited_inForgotPassword() {
            org.springframework.mock.web.MockHttpServletRequest mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
            mockRequest.addHeader("X-Forwarded-For", "50.60.70.80");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(mockRequest));

            try {
                ForgotPasswordRequest req = new ForgotPasswordRequest();
                req.setEmail("test@example.com");

                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_FORGOT_PASSWORD + "test@example.com"))).thenReturn(false);
                when(redisService.isRateLimited(eq(RedisKeyConstants.AUTH_RATE_LIMIT_FORGOT_PASSWORD + "ip:50.60.70.80"))).thenReturn(true);

                ForgotPasswordResponse resp = authService.forgotPassword(req);
                assertThat(resp.getMessage()).isNotNull();
                verify(userRepository, never()).findByEmailIgnoreCaseAndDeletedAtIsNull(any());
            } finally {
                org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        void continuesSuccessfully_whenEmailServiceThrows() {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("test@example.com");

            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(activeUser()));
            doThrow(new RuntimeException("SMTP error"))
                    .when(emailService).sendPasswordResetMail(any(), any());

            // Không được throw ra ngoài dù email lỗi
            ForgotPasswordResponse resp = authService.forgotPassword(req);
            assertThat(resp.getMessage()).isNotNull();
        }

        @Test
        void returnsSuccess_whenUserExistsButNotActive() {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("test@example.com");

            UserEntity user = activeUser();
            user.setStatus(UserStatus.PENDING_ACTIVATION);

            when(redisService.isRateLimited(anyString())).thenReturn(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com"))
                    .thenReturn(Optional.of(user));

            ForgotPasswordResponse resp = authService.forgotPassword(req);

            assertThat(resp.getMessage()).contains("email exists");
            verify(emailService, never()).sendPasswordResetMail(any(), any());
            verify(redisService, never()).savePasswordResetToken(anyString(), any());
        }
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Nested
    class ResetPassword {

        @Test
        void resetsPassword_whenTokenValid() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("valid-token");
            req.setNewPassword("NewPass123!");
            req.setConfirmPassword("NewPass123!");

            UserEntity user = activeUser();
            when(redisService.getPasswordResetUserId(anyString())).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("NewPass123!")).thenReturn("new-hashed");

            ResetPasswordResponse resp = authService.resetPassword(req);

            assertThat(resp.getMessage()).contains("reset successfully");
            assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
            verify(redisService).deletePasswordResetToken(anyString());
            verify(redisService).deleteRefreshToken(userId);
        }

        @Test
        void throws_whenPasswordMismatch() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("Pass1!");
            req.setConfirmPassword("Pass2!");

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("does not match");
        }

        @Test
        void throws_whenTokenNotInRedis() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("expired-token");
            req.setNewPassword("Pass1!");
            req.setConfirmPassword("Pass1!");

            when(redisService.getPasswordResetUserId(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("invalid or expired");
        }

        @Test
        void throws_whenUserDeleted() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("token");
            req.setNewPassword("Pass1!");
            req.setConfirmPassword("Pass1!");

            UserEntity user = activeUser();
            user.setDeletedAt(OffsetDateTime.now());

            when(redisService.getPasswordResetUserId(anyString())).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── activateAccount ───────────────────────────────────────────────────────

    @Nested
    class ActivateAccount {

        @Test
        void activatesUser_whenTokenValid() {
            UserEntity user = activeUser();
            user.setStatus(UserStatus.PENDING_ACTIVATION);
            user.setActivationToken("valid-token");
            user.setActivationTokenExpiresAt(OffsetDateTime.now().plusHours(1));

            when(userRepository.findByActivationTokenAndDeletedAtIsNull("valid-token"))
                    .thenReturn(Optional.of(user));

            ActivateAccountResponse resp = authService.activateAccount("valid-token");

            assertThat(resp.getMessage()).contains("activated");
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getActivationToken()).isNull();
        }

        @Test
        void throws_whenTokenBlank() {
            assertThatThrownBy(() -> authService.activateAccount(""))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("required");
        }

        @Test
        void throws_whenTokenNull() {
            assertThatThrownBy(() -> authService.activateAccount(null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void throws_whenTokenNotFound() {
            when(userRepository.findByActivationTokenAndDeletedAtIsNull("bad-token"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.activateAccount("bad-token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("invalid or expired");
        }

        @Test
        void throws_whenTokenExpired() {
            UserEntity user = activeUser();
            user.setStatus(UserStatus.PENDING_ACTIVATION);
            user.setActivationToken("expired-token");
            user.setActivationTokenExpiresAt(OffsetDateTime.now().minusHours(1));

            when(userRepository.findByActivationTokenAndDeletedAtIsNull("expired-token"))
                    .thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.activateAccount("expired-token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("invalid or expired");
        }

        @Test
        void throws_whenUserDisabled() {
            UserEntity user = activeUser();
            user.setStatus(UserStatus.DISABLED);
            user.setActivationToken("token");
            user.setDisabledAt(OffsetDateTime.now());
            user.setActivationTokenExpiresAt(OffsetDateTime.now().plusHours(1));

            when(userRepository.findByActivationTokenAndDeletedAtIsNull("token"))
                    .thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.activateAccount("token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("disabled");
        }

        @Test
        void throws_whenActivationTokenExpiryIsNull() {
            UserEntity user = activeUser();
            user.setStatus(UserStatus.PENDING_ACTIVATION);
            user.setActivationToken("no-expiry-token");
            user.setActivationTokenExpiresAt(null);

            when(userRepository.findByActivationTokenAndDeletedAtIsNull("no-expiry-token"))
                    .thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.activateAccount("no-expiry-token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("invalid or expired");
        }
    }
}
