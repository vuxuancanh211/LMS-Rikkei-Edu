package project.lms_rikkei_edu.modules.auth.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.JwtService;
import project.lms_rikkei_edu.infrastructure.email.EmailService;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.auth.dto.request.ForgotPasswordRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.LoginRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.RefreshTokenRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.ResetPasswordRequest;
import project.lms_rikkei_edu.modules.auth.dto.response.ActivateAccountResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.ForgotPasswordResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.LoginResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.LogoutResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.RefreshTokenResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.ResetPasswordResponse;
import project.lms_rikkei_edu.modules.auth.service.AuthService;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.mapper.UserMapper;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String FORGOT_PASSWORD_MESSAGE = "If the email exists, a password reset link has been sent";
    private static final String TOO_MANY_ATTEMPTS_MESSAGE = "Too many attempts, please try again later";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final RedisService redisService;
    private final EmailService emailService;
    private final CurrentUserProvider currentUserProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.password-reset-url}")
    private String passwordResetUrl;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        enforceRateLimit(RedisKeyConstants.AUTH_RATE_LIMIT_LOGIN + email);

        UserEntity user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Email or password is incorrect"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Email or password is incorrect");
        }

        if (user.getStatus() != UserStatus.ACTIVE || user.getDisabledAt() != null) {
            throw new BusinessException("User account is not active");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        String refreshToken = generateRefreshToken(user.getId());
        redisService.saveRefreshToken(user.getId(), hashToken(refreshToken));

        return LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationSeconds())
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    public UserResponse getMe() {
        UUID currentUserId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));

        UserEntity user = userRepository.findById(currentUserId)
                .filter(existingUser -> existingUser.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE || user.getDisabledAt() != null) {
            throw new BusinessException("User account is not active");
        }

        return userMapper.toResponse(user);
    }

    @Override
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        UUID userId = extractUserIdFromRefreshToken(request.getRefreshToken());
        String requestTokenHash = hashToken(request.getRefreshToken());
        String storedTokenHash = redisService.getRefreshToken(userId)
                .orElseThrow(() -> new BusinessException("Refresh token is invalid or expired"));

        if (!isSameHash(requestTokenHash, storedTokenHash)) {
            throw new BusinessException("Refresh token is invalid or expired");
        }

        UserEntity user = userRepository.findById(userId)
                .filter(existingUser -> existingUser.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("Refresh token is invalid or expired"));

        if (user.getStatus() != UserStatus.ACTIVE || user.getDisabledAt() != null) {
            redisService.deleteRefreshToken(userId);
            throw new BusinessException("User account is not active");
        }

        String newRefreshToken = generateRefreshToken(user.getId());
        redisService.saveRefreshToken(user.getId(), hashToken(newRefreshToken));

        return RefreshTokenResponse.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationSeconds())
                .build();
    }

    @Override
    public LogoutResponse logout(String authorizationHeader) {
        String accessToken = jwtService.resolveToken(authorizationHeader);
        if (accessToken == null) {
            throw new BusinessException("Authorization token is required");
        }

        String jti = jwtService.extractJti(accessToken);
        if (jti == null || jti.isBlank()) {
            throw new BusinessException("Invalid access token");
        }

        Date tokenExpiration = jwtService.extractExpiration(accessToken);
        UUID userId = jwtService.extractUserId(accessToken);

        redisService.blacklistAccessToken(jti, tokenExpiration);
        redisService.deleteRefreshToken(userId);

        return LogoutResponse.builder()
                .message("Logout successfully")
                .build();
    }

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (redisService.isRateLimited(RedisKeyConstants.AUTH_RATE_LIMIT_FORGOT_PASSWORD + email)) {
            return ForgotPasswordResponse.builder()
                    .message(FORGOT_PASSWORD_MESSAGE)
                    .build();
        }

        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getDisabledAt() == null)
                .ifPresent(user -> {
                    String token = generateResetToken();
                    String tokenHash = hashToken(token);
                    String resetLink = buildResetLink(token);

                    redisService.savePasswordResetToken(tokenHash, user.getId());
                    emailService.sendPasswordResetMail(user.getEmail(), resetLink);
                });

        return ForgotPasswordResponse.builder()
                .message(FORGOT_PASSWORD_MESSAGE)
                .build();
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Password confirmation does not match");
        }

        String tokenHash = hashToken(request.getToken());
        UUID userId = redisService.getPasswordResetUserId(tokenHash)
                .orElseThrow(() -> new BusinessException("Password reset token is invalid or expired"));

        UserEntity user = userRepository.findById(userId)
                .filter(existingUser -> existingUser.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("Password reset token is invalid or expired"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(OffsetDateTime.now());

        redisService.deletePasswordResetToken(tokenHash);
        redisService.deleteRefreshToken(user.getId());

        return ResetPasswordResponse.builder()
                .message("Password has been reset successfully")
                .build();
    }

    @Override
    @Transactional
    public ActivateAccountResponse activateAccount(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("Activation token is required");
        }

        UserEntity user = userRepository.findByActivationTokenAndDeletedAtIsNull(token)
                .orElseThrow(() -> new BusinessException("Activation token is invalid or expired"));

        if (user.getDisabledAt() != null) {
            throw new BusinessException("User account is disabled");
        }

        if (user.getActivationTokenExpiresAt() == null
                || user.getActivationTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("Activation token is invalid or expired");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setActivationToken(null);
        user.setActivationTokenExpiresAt(null);
        user.setUpdatedAt(OffsetDateTime.now());

        return ActivateAccountResponse.builder()
                .message("Account activated successfully")
                .build();
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateRefreshToken(UUID userId) {
        return userId + "." + generateResetToken();
    }

    private UUID extractUserIdFromRefreshToken(String refreshToken) {
        int separatorIndex = refreshToken.indexOf('.');
        if (separatorIndex <= 0) {
            throw new BusinessException("Refresh token is invalid or expired");
        }

        try {
            return UUID.fromString(refreshToken.substring(0, separatorIndex));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Refresh token is invalid or expired");
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String buildResetLink(String token) {
        String separator = passwordResetUrl.contains("?") ? "&" : "?";
        return passwordResetUrl + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private boolean isSameHash(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void enforceRateLimit(String key) {
        if (redisService.isRateLimited(key)) {
            throw new BusinessException(TOO_MANY_ATTEMPTS_MESSAGE);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
