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
import project.lms_rikkei_edu.infrastructure.email.EmailAsyncService;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_TEMP_PASSWORD = "123456@";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final RedisService redisService;
    private final EmailService emailService;
    private final EmailAsyncService emailAsyncService;
    private final JwtService jwtService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogRepository auditLogRepository;

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
    public boolean existsByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent();
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumberAndDeletedAtIsNull(phoneNumber).isPresent();
    }

    @Override
    @Transactional
    public UserResponse createUser(UUID adminId, AdminUserCreateRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            throw new BusinessException("Email này đã được sử dụng");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Vai trò không hợp lệ: " + request.getRole());
        }

        String tempPassword = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            if (userRepository.findByPhoneNumberAndDeletedAtIsNull(request.getPhoneNumber()).isPresent()) {
                throw new BusinessException("Số điện thoại này đã được sử dụng");
            }
        }

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

        emailService.sendNewAccountMail(user.getEmail(), user.getFullName(), tempPassword);

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
                    throw new BusinessException("Email này đã được sử dụng");
                }
                user.setEmail(newEmail);
            }
        }
        if (request.getPhoneNumber() != null) {
            String newPhone = request.getPhoneNumber().trim();
            if (!newPhone.equals(user.getPhoneNumber())) {
                if (userRepository.findByPhoneNumberAndDeletedAtIsNull(newPhone).isPresent()) {
                    throw new BusinessException("Số điện thoại này đã được sử dụng");
                }
                user.setPhoneNumber(newPhone);
            }
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
                throw new BusinessException("Vai trò không hợp lệ: " + request.getRole());
            }

            if (adminId.equals(userId) && newRole != user.getRole()) {
                throw new BusinessException("Quản trị viên không thể tự đổi vai trò của chính mình");
            }

            if (user.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
                long remainingAdmins = userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN);
                if (remainingAdmins <= 1) {
                    throw new BusinessException("Không thể hạ cấp quản trị viên cuối cùng");
                }
            }

            user.setRole(newRole);
        }
        if (request.getStatus() != null) {
            UserStatus newStatus;
            try {
                newStatus = UserStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Trạng thái không hợp lệ: " + request.getStatus());
            }

            if (user.getStatus() == UserStatus.DELETED && newStatus != UserStatus.DELETED) {
                throw new BusinessException("Không thể thay đổi trạng thái của tài khoản đã xóa");
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
    public MessageResponse resetPassword(UUID adminId, UUID userId, ResetPasswordRequest request) {
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng"));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException("Không thể đặt lại mật khẩu cho tài khoản đã xóa");
        }

        String tempPassword = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);

        user.setPasswordHash(passwordHash);
        user.setPasswordChangedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        revokeUserSessions(userId);

        invalidateUserCaches(userId);

        emailService.sendAdminPasswordResetMail(user.getEmail(), user.getFullName(), tempPassword);

        writeAuditLog(adminId, "RESET_PASSWORD", "USER", userId,
                null, null,
                request.getReason() != null ? request.getReason() : "Admin reset user password");

        return MessageResponse.builder()
                .message("Mật khẩu mới đã được gửi tới email của người dùng")
                .build();
    }

    @Override
    @Transactional
    public List<UserResponse> batchCreateUsers(UUID adminId, List<AdminUserCreateRequest> requests) {
        String tempPassword = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);

        List<UserEntity> users = new ArrayList<>();
        for (AdminUserCreateRequest request : requests) {
            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setEmail(request.getEmail().trim().toLowerCase());
            user.setFullName(request.getFullName().trim());
            user.setPasswordHash(passwordHash);
            user.setRole(UserRole.valueOf(request.getRole().toUpperCase()));
            user.setStatus(UserStatus.ACTIVE);
            user.setPhoneNumber(request.getPhoneNumber());
            user.setCreatedBy(adminId);
            user.setCreatedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
            users.add(user);
        }

        userRepository.saveAll(users);

        for (UserEntity user : users) {
            emailAsyncService.sendNewAccountMailAsync(user.getEmail(), user.getFullName(), tempPassword);
            writeAuditLog(adminId, "CREATE_USER", "USER", user.getId(),
                    null, String.format("{\"email\":\"%s\",\"role\":\"%s\"}", user.getEmail(), user.getRole()),
                    "Admin created user account via CSV import");
        }

        return users.stream().map(userMapper::toResponse).toList();
    }

    private UserEntity findActiveUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng"));
    }

    private String generateTemporaryPassword() {
        return DEFAULT_TEMP_PASSWORD;
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
