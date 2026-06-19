package project.lms_rikkei_edu.modules.user.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.common.dto.response.PagedResponse;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.JwtService;
import project.lms_rikkei_edu.infrastructure.email.EmailService;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.audit.entity.AuditLogEntity;
import project.lms_rikkei_edu.modules.audit.repository.AuditLogRepository;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserCreateRequest;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserListRequest;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserUpdateRequest;
import project.lms_rikkei_edu.modules.user.dto.request.ResetPasswordRequest;
import project.lms_rikkei_edu.modules.user.dto.response.AdminUserDetailResponse;
import project.lms_rikkei_edu.modules.user.dto.response.MessageResponse;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.mapper.UserMapper;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import project.lms_rikkei_edu.modules.user.specification.UserSpecification;
import project.lms_rikkei_edu.modules.user.service.UserService;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&!";
    private static final int PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final RedisService redisService;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogRepository auditLogRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.password-reset-url}")
    private String passwordResetUrl;

    @Override
    public PagedResponse<UserResponse> getUsers(AdminUserListRequest request) {
        String sortBy = mapSortField(request.getSortBy());
        Sort.Direction sortDir = "asc".equalsIgnoreCase(request.getSortDir())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize(),
                Sort.by(sortDir, sortBy));

        Page<UserEntity> page = userRepository.findAll(
                UserSpecification.withDynamicQuery(
                        request.getSearch(), request.getRole(), request.getStatus()),
                pageable);

        List<UserResponse> items = page.getContent().stream()
                .map(userMapper::toResponse)
                .toList();

        return new PagedResponse<>(items, page.getTotalElements(), request.getPage(), request.getSize());
    }

    @Override
    public AdminUserDetailResponse getUserDetail(UUID userId) {
        UserEntity user = findActiveUser(userId);
        return userMapper.toAdminDetailResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createUser(UUID adminId, AdminUserCreateRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            throw new BusinessException("Email already exists");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid role: " + request.getRole());
        }

        String tempPassword = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFullName(request.getFullName().trim());
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setCreatedBy(adminId);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        userRepository.save(user);

        emailService.sendTemporaryPasswordMail(user.getEmail(), tempPassword);

        writeAuditLog(adminId, "CREATE_USER", "USER", user.getId(),
                null, String.format("{\"email\":\"%s\",\"role\":\"%s\"}", email, role),
                "Admin created user account");

        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID adminId, UUID userId, AdminUserUpdateRequest request) {
        UserEntity user = findActiveUser(userId);

        String beforeJson = captureUserState(user);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getEmail() != null) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equals(user.getEmail())) {
                if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(newEmail).isPresent()) {
                    throw new BusinessException("Email already exists");
                }
                user.setEmail(newEmail);
            }
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getBirthDate() != null) {
            user.setBirthDate(request.getBirthDate());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getRole() != null) {
            UserRole newRole;
            try {
                newRole = UserRole.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid role: " + request.getRole());
            }

            if (adminId.equals(userId) && newRole != user.getRole()) {
                throw new BusinessException("Admin cannot change their own role");
            }

            if (user.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
                long remainingAdmins = userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN);
                if (remainingAdmins <= 1) {
                    throw new BusinessException("Cannot demote the last active ADMIN");
                }
            }

            user.setRole(newRole);
        }
        if (request.getStatus() != null) {
            UserStatus newStatus;
            try {
                newStatus = UserStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid status: " + request.getStatus());
            }

            if (user.getStatus() == UserStatus.DELETED && newStatus != UserStatus.DELETED) {
                throw new BusinessException("Cannot change status from DELETED to active status");
            }

            if (newStatus == UserStatus.DISABLED) {
                user.setDisabledAt(OffsetDateTime.now());
                user.setDisabledBy(adminId);
            } else if (user.getStatus() == UserStatus.DISABLED && newStatus == UserStatus.ACTIVE) {
                user.setDisabledAt(null);
                user.setDisabledBy(null);
                user.setDisabledReason(null);
            }

            user.setStatus(newStatus);
        }

        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        invalidateUserCaches(userId);

        writeAuditLog(adminId, "UPDATE_USER", "USER", userId,
                beforeJson, captureUserState(user),
                "Admin updated user account");

        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public MessageResponse deleteUser(UUID adminId, UUID userId) {
        if (adminId.equals(userId)) {
            throw new BusinessException("Admin cannot delete their own account");
        }

        UserEntity user = findActiveUser(userId);

        if (user.getRole() == UserRole.ADMIN) {
            long remainingAdmins = userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN);
            if (remainingAdmins <= 1) {
                throw new BusinessException("Cannot delete the last active ADMIN");
            }
        }

        String beforeJson = captureUserState(user);

        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        revokeUserSessions(userId);

        invalidateUserCaches(userId);

        writeAuditLog(adminId, "DELETE_USER", "USER", userId,
                beforeJson, captureUserState(user),
                "Admin soft-deleted user account");

        return MessageResponse.builder()
                .message("User account has been deleted successfully")
                .build();
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(UUID adminId, UUID userId, ResetPasswordRequest request) {
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException("Cannot reset password for a deleted user");
        }

        String tempPassword = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);

        user.setPasswordHash(passwordHash);
        user.setPasswordChangedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        revokeUserSessions(userId);

        invalidateUserCaches(userId);

        emailService.sendTemporaryPasswordMail(user.getEmail(), tempPassword);

        writeAuditLog(adminId, "RESET_PASSWORD", "USER", userId,
                null, null,
                request.getReason() != null ? request.getReason() : "Admin reset user password");

        return MessageResponse.builder()
                .message("Temporary password has been sent to the user's email")
                .build();
    }

    private UserEntity findActiveUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("User not found"));
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        sb.append(PASSWORD_CHARS.charAt(secureRandom.nextInt(26))); // uppercase
        sb.append(PASSWORD_CHARS.charAt(26 + secureRandom.nextInt(26))); // lowercase
        sb.append(PASSWORD_CHARS.charAt(52 + secureRandom.nextInt(10))); // digit
        sb.append(PASSWORD_CHARS.charAt(62 + secureRandom.nextInt(5))); // special
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(secureRandom.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private String mapSortField(String sortBy) {
        if (sortBy == null) return "createdAt";
        return switch (sortBy) {
            case "full_name" -> "fullName";
            case "email" -> "email";
            default -> "createdAt";
        };
    }

    private void revokeUserSessions(UUID userId) {
        String tokensKey = RedisKeyConstants.USER_TOKENS + userId;
        redisService.delete(tokensKey);
        redisService.deleteRefreshToken(userId);
    }

    private void invalidateUserCaches(UUID userId) {
        redisService.delete(RedisKeyConstants.ADMIN_USER_DETAIL + userId);
        redisService.delete(RedisKeyConstants.USER_PROFILE + userId);
    }

    private void writeAuditLog(UUID actorId, String action, String targetType,
                               UUID targetId, String payloadBefore, String payloadAfter,
                               String reason) {
        try {
            AuditLogEntity log = new AuditLogEntity();
            log.setId(UUID.randomUUID());
            log.setActorId(actorId);
            log.setAction(action);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setPayloadBefore(payloadBefore);
            log.setPayloadAfter(payloadAfter);
            log.setReason(reason);
            log.setCreatedAt(OffsetDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception ignored) {
            // Audit log failure should not break the main operation
        }
    }

    private String captureUserState(UserEntity user) {
        return String.format(
                "{\"email\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\",\"status\":\"%s\"}",
                user.getEmail(), user.getFullName(),
                user.getRole(), user.getStatus());
    }
}
