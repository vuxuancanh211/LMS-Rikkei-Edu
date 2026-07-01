package project.lms_rikkei_edu.modules.profile.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.profile.dto.request.ChangePasswordRequest;
import project.lms_rikkei_edu.modules.profile.dto.request.ProfileUpdateRequest;
import project.lms_rikkei_edu.modules.profile.dto.response.ProfileResponse;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.mapper.UserMapper;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RedisService redisService;
    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;

    private ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(
                userRepository, userMapper, passwordEncoder,
                redisService, s3Client, s3Presigner
        );
        ReflectionTestUtils.setField(profileService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(profileService, "presignedUrlExpiry", 3600L);
    }

    // ── getProfile ───────────────────────────────────────────────────────────

    @Test
    void getProfile_success() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        var response = profileResponse(userId);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userMapper.toProfileResponse(user)).thenReturn(response);

        ProfileResponse result = profileService.getProfile(userId);

        assertThat(result).isSameAs(response);
    }

    @Test
    void getProfile_userNotFound() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không tìm thấy người dùng");
    }

    @Test
    void getProfile_avatarUrlIsS3Key_generatesPresignedUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        var response = profileResponse(userId);
        response.setAvatarUrl("avatars/" + userId + ".jpg");

        var presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URI("https://s3.test/presigned/" + userId).toURL());
        when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(presignedRequest);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userMapper.toProfileResponse(user)).thenReturn(response);

        ProfileResponse result = profileService.getProfile(userId);

        assertThat(result.getAvatarUrl()).isEqualTo("https://s3.test/presigned/" + userId);
    }

    @Test
    void getProfile_avatarUrlIsHttp_doesNotPresign() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        var response = profileResponse(userId);
        response.setAvatarUrl("https://cdn.example.com/avatar.jpg");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userMapper.toProfileResponse(user)).thenReturn(response);

        ProfileResponse result = profileService.getProfile(userId);

        assertThat(result.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.jpg");
        verify(s3Presigner, never()).presignGetObject(any(Consumer.class));
    }

    // ── updateProfile ────────────────────────────────────────────────────────

    @Test
    void updateProfile_success_allFields() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        var request = profileUpdateRequest("Nguyen Van A", "0912345678",
                LocalDate.of(2000, 1, 15), "MALE", "Hello world");
        var response = profileResponse(userId);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0912345678"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toProfileResponse(any(UserEntity.class))).thenReturn(response);

        ProfileResponse result = profileService.updateProfile(userId, request);

        assertThat(result).isSameAs(response);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(saved.getPhoneNumber()).isEqualTo("0912345678");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 15));
        assertThat(saved.getGender()).isEqualTo("MALE");
        assertThat(saved.getBio()).isEqualTo("Hello world");
    }

    @Test
    void updateProfile_success_partial() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        user.setFullName("Old Name");
        user.setPhoneNumber("0900000000");
        var request = profileUpdateRequest("New Name", null, null, null, null);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toProfileResponse(any(UserEntity.class))).thenReturn(profileResponse(userId));

        profileService.updateProfile(userId, request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getFullName()).isEqualTo("New Name");
        assertThat(saved.getPhoneNumber()).isEqualTo("0900000000");
    }

    @Test
    void updateProfile_duplicatePhone_differentUser() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        user.setPhoneNumber("0900000000");
        var request = profileUpdateRequest(null, "0999999999", null, null, null);

        var otherUser = new UserEntity();
        otherUser.setId(UUID.randomUUID());

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0999999999"))
                .thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> profileService.updateProfile(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Số điện thoại này đã được sử dụng");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_duplicatePhone_sameUser_allowed() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        user.setPhoneNumber("0999999999");
        var request = profileUpdateRequest(null, "0999999999", null, null, null);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0999999999"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toProfileResponse(any(UserEntity.class))).thenReturn(profileResponse(userId));

        profileService.updateProfile(userId, request);

        verify(userRepository).save(any(UserEntity.class));
    }

    // ── changePassword ───────────────────────────────────────────────────────

    @Test
    void changePassword_success() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        user.setPasswordHash("old_hash");
        var request = changePasswordRequest("old_pass", "new_pass");
        var response = profileResponse(userId);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old_pass", "old_hash")).thenReturn(true);
        when(passwordEncoder.encode("new_pass")).thenReturn("new_hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toProfileResponse(any(UserEntity.class))).thenReturn(response);

        ProfileResponse result = profileService.changePassword(userId, request);

        assertThat(result).isSameAs(response);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("new_hash");

        verify(redisService).delete(RedisKeyConstants.USER_TOKENS + userId);
        verify(redisService).deleteRefreshToken(userId);
    }

    @Test
    void changePassword_wrongCurrentPassword() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        user.setPasswordHash("old_hash");
        var request = changePasswordRequest("wrong_pass", "new_pass");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_pass", "old_hash")).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Mật khẩu hiện tại không đúng");
        verify(userRepository, never()).save(any());
        verify(redisService, never()).delete(anyString());
    }

    // ── uploadAvatar ─────────────────────────────────────────────────────────

    @Test
    void uploadAvatar_success() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        var file = mock(MultipartFile.class);
        var response = profileResponse(userId);

        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("avatar.jpg");
        when(file.getSize()).thenReturn(1024L);
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[1024]));

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toProfileResponse(any(UserEntity.class))).thenReturn(response);

        ProfileResponse result = profileService.uploadAvatar(userId, file);

        assertThat(result).isSameAs(response);
        verify(s3Client).putObject(any(Consumer.class), any(RequestBody.class));
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void uploadAvatar_emptyFile() {
        UUID userId = UUID.randomUUID();
        var file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> profileService.uploadAvatar(userId, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File không được để trống");
        verify(s3Client, never()).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    void uploadAvatar_nonImageFile() {
        UUID userId = UUID.randomUUID();
        var file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");

        assertThatThrownBy(() -> profileService.uploadAvatar(userId, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Chỉ chấp nhận file ảnh");
        verify(s3Client, never()).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    void uploadAvatar_oldKeyEqualsNewKey_doesNotDelete() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        user.setAvatarUrl("avatars/" + userId + ".jpg");
        var file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getSize()).thenReturn(2048L);
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[2048]));

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toProfileResponse(any(UserEntity.class))).thenReturn(profileResponse(userId));

        profileService.uploadAvatar(userId, file);

        verify(s3Client, never()).deleteObject(any(Consumer.class));
    }

    @Test
    void uploadAvatar_withOldDifferentKey_deletesOldKey() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId);
        user.setAvatarUrl("avatars/old_" + userId + ".jpg");
        var file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("new.jpg");
        when(file.getSize()).thenReturn(1024L);
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[1024]));

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(s3Client.putObject(any(Consumer.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toProfileResponse(any(UserEntity.class))).thenReturn(profileResponse(userId));
        var presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URI("https://s3.test/presigned/" + userId).toURL());
        when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(presignedRequest);

        profileService.uploadAvatar(userId, file);

        verify(s3Client).deleteObject(any(Consumer.class));
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private UserEntity userEntity(UUID id) {
        var user = new UserEntity();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setFullName("Test User");
        user.setPasswordHash("password");
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }

    private ProfileResponse profileResponse(UUID id) {
        var resp = new ProfileResponse();
        resp.setId(id);
        resp.setEmail(id + "@example.com");
        resp.setFullName("Test User");
        resp.setRole("STUDENT");
        resp.setStatus("ACTIVE");
        resp.setCreatedAt(OffsetDateTime.now());
        return resp;
    }

    private ProfileUpdateRequest profileUpdateRequest(String fullName, String phoneNumber,
                                                       LocalDate birthDate, String gender, String bio) {
        var req = new ProfileUpdateRequest();
        req.setFullName(fullName);
        req.setPhoneNumber(phoneNumber);
        req.setBirthDate(birthDate);
        req.setGender(gender);
        req.setBio(bio);
        return req;
    }

    private ChangePasswordRequest changePasswordRequest(String currentPassword, String newPassword) {
        var req = new ChangePasswordRequest();
        req.setCurrentPassword(currentPassword);
        req.setNewPassword(newPassword);
        return req;
    }
}
