package project.lms_rikkei_edu.modules.profile.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.profile.dto.request.ChangePasswordRequest;
import project.lms_rikkei_edu.modules.profile.dto.request.ProfileUpdateRequest;
import project.lms_rikkei_edu.modules.profile.dto.response.ProfileResponse;
import project.lms_rikkei_edu.modules.profile.service.ProfileService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.mapper.UserMapper;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.presigned-url-expiry}")
    private long presignedUrlExpiry;

    @Override
    @Cacheable(value = "user-profile", key = "#userId")
    public ProfileResponse getProfile(UUID userId) {
        UserEntity user = findActiveUser(userId);
        return resolveAvatarUrl(userMapper.toProfileResponse(user));
    }

    @Override
    @CacheEvict(value = "user-profile", key = "#userId", beforeInvocation = true)
    @Transactional
    public ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request) {
        UserEntity user = findActiveUser(userId);

        if (request.getFullName() != null) user.setFullName(request.getFullName().trim());
        if (request.getPhoneNumber() != null) {
            if (userRepository.findByPhoneNumberAndDeletedAtIsNull(request.getPhoneNumber())
                    .filter(existing -> !existing.getId().equals(userId))
                    .isPresent()) {
                throw new BusinessException("Số điện thoại này đã được sử dụng");
            }
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getBirthDate() != null) user.setBirthDate(request.getBirthDate());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getBio() != null) user.setBio(request.getBio());

        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        return resolveAvatarUrl(userMapper.toProfileResponse(user));
    }

    @Override
    @CacheEvict(value = "user-profile", key = "#userId", beforeInvocation = true)
    @Transactional
    public ProfileResponse changePassword(UUID userId, ChangePasswordRequest request) {
        UserEntity user = findActiveUser(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu hiện tại không đúng");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        String tokensKey = RedisKeyConstants.USER_TOKENS + userId;
        redisService.delete(tokensKey);
        redisService.deleteRefreshToken(userId);

        return resolveAvatarUrl(userMapper.toProfileResponse(user));
    }

    @Override
    @CacheEvict(value = "user-profile", key = "#userId", beforeInvocation = true)
    @Transactional
    public ProfileResponse uploadAvatar(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File không được để trống");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("Chỉ chấp nhận file ảnh");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }

        String key = "avatars/" + userId + extension;

        UserEntity user = findActiveUser(userId);
        String oldKey = user.getAvatarUrl();

        try {
            s3Client.putObject(
                    req -> req.bucket(bucket).key(key).contentType(contentType),
                    software.amazon.awssdk.core.sync.RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded avatar for user {} to key={}", userId, key);
        } catch (IOException | SdkException e) {
            log.error("Failed to upload avatar for user {}: {}", userId, e.getMessage(), e);
            throw new BusinessException("Upload ảnh thất bại");
        }

        if (oldKey != null && !oldKey.startsWith("http") && !oldKey.equals(key)) {
            try {
                s3Client.deleteObject(req -> req.bucket(bucket).key(oldKey));
                log.info("Deleted old avatar key={} for user {}", oldKey, userId);
            } catch (Exception e) {
                log.warn("Failed to delete old avatar key={} for user {}: {}", oldKey, userId, e.getMessage());
            }
        }

        user.setAvatarUrl(key);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        return resolveAvatarUrl(userMapper.toProfileResponse(user));
    }

    private UserEntity findActiveUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng"));
    }

    private ProfileResponse resolveAvatarUrl(ProfileResponse response) {
        if (response.getAvatarUrl() != null && !response.getAvatarUrl().startsWith("http")) {
            String presignedUrl = s3Presigner.presignGetObject(r -> r
                    .signatureDuration(Duration.ofSeconds(presignedUrlExpiry))
                    .getObjectRequest(get -> get.bucket(bucket).key(response.getAvatarUrl()))
            ).url().toString();
            response.setAvatarUrl(presignedUrl);
        }
        return response;
    }
}
