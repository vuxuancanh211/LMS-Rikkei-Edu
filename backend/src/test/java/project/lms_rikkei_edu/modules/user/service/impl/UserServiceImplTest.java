package project.lms_rikkei_edu.modules.user.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;
import project.lms_rikkei_edu.common.dto.response.PagedResponse;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.JwtService;
import project.lms_rikkei_edu.infrastructure.email.EmailAsyncService;
import project.lms_rikkei_edu.infrastructure.email.EmailService;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.CourseEnrollmentEntity;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RedisService redisService;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private EmailAsyncService emailAsyncService;
    @Mock
    private JwtService jwtService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AuditLogRepository auditLogRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userRepository, passwordEncoder, userMapper,
                redisService, emailService, emailAsyncService,
                jwtService, currentUserProvider, auditLogRepository,
                courseRepository, courseEnrollmentRepository
        );
        ReflectionTestUtils.setField(userService, "defaultTempPassword", "123456@");
    }

    // ── getUsers ─────────────────────────────────────────────────────────────

    @Test
    void getUsersWithFilters() {
        var request = adminUserListRequest("john", "ADMIN", "ACTIVE", "email", "asc");
        var user = userEntity(UUID.randomUUID(), UserRole.ADMIN, UserStatus.ACTIVE);
        var page = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);
        var response = userResponse(user.getId());

        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(response);

        PagedResponse<UserResponse> result = userService.getUsers(request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst()).isSameAs(response);
        assertThat(result.getTotalRecords()).isEqualTo(1);
        assertThat(result.getPage()).isEqualTo(1);
        verify(userRepository).findAll(any(Specification.class),
                eq(PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "email"))));
    }

    @Test
    void getUsersWithDefaultSort() {
        var request = adminUserListRequest(null, null, null, null, null);
        var user = userEntity(UUID.randomUUID(), UserRole.STUDENT, UserStatus.ACTIVE);
        var page = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);
        var response = userResponse(user.getId());

        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(response);

        PagedResponse<UserResponse> result = userService.getUsers(request);

        assertThat(result.getItems()).hasSize(1);
        verify(userRepository).findAll(any(Specification.class),
                eq(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    // ── getUserDetail ────────────────────────────────────────────────────────

    @Test
    void getUserDetailSuccess() {
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId, UserRole.INSTRUCTOR, UserStatus.ACTIVE);
        var detail = adminUserDetailResponse(userId);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userMapper.toAdminDetailResponse(user)).thenReturn(detail);

        AdminUserDetailResponse result = userService.getUserDetail(userId);

        assertThat(result).isSameAs(detail);
    }

    @Test
    void getUserDetailNotFound() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserDetail(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không tìm thấy người dùng");
    }

    // ── existsByEmail ────────────────────────────────────────────────────────

    @Test
    void existsByEmailReturnsTrue() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@test.com"))
                .thenReturn(Optional.of(new UserEntity()));

        assertThat(userService.existsByEmail("test@test.com")).isTrue();
    }

    @Test
    void existsByEmailReturnsFalse() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@test.com"))
                .thenReturn(Optional.empty());

        assertThat(userService.existsByEmail("test@test.com")).isFalse();
    }

    // ── existsByPhoneNumber ──────────────────────────────────────────────────

    @Test
    void existsByPhoneNumberReturnsTrue() {
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0934567890"))
                .thenReturn(Optional.of(new UserEntity()));

        assertThat(userService.existsByPhoneNumber("0934567890")).isTrue();
    }

    @Test
    void existsByPhoneNumberReturnsFalse() {
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0934567890"))
                .thenReturn(Optional.empty());

        assertThat(userService.existsByPhoneNumber("0934567890")).isFalse();
    }

    // ── createUser ───────────────────────────────────────────────────────────

    @Test
    void createUserSuccess() {
        UUID adminId = UUID.randomUUID();
        var request = adminUserCreateRequest("john@test.com", "John", "STUDENT", "0934567890");
        var course = courseEntity(request.getCourseId(), "Khoá học A");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("john@test.com"))
                .thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0934567890"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_123456@");
        when(courseRepository.findById(request.getCourseId())).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.existsByCourseIdAndStudentId(eq(course.getId()), any())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var expectedResponse = userResponse(UUID.randomUUID());
        when(userMapper.toResponse(any(UserEntity.class))).thenReturn(expectedResponse);

        UserResponse result = userService.createUser(adminId, request);

        assertThat(result).isSameAs(expectedResponse);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("john@test.com");
        assertThat(saved.getFullName()).isEqualTo("John");
        assertThat(saved.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getPasswordHash()).isEqualTo("hashed_123456@");
        assertThat(saved.getCreatedBy()).isEqualTo(adminId);

        verify(courseEnrollmentRepository).save(any(CourseEnrollmentEntity.class));
        verify(emailService).sendNewAccountMail(eq("john@test.com"), eq("John"), anyString(), eq("Khoá học A"));
        verify(auditLogRepository).save(any(AuditLogEntity.class));
    }

    @Test
    void createUserDuplicateEmail() {
        UUID adminId = UUID.randomUUID();
        var request = adminUserCreateRequest("john@test.com", "John", "STUDENT", "0934567890");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("john@test.com"))
                .thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> userService.createUser(adminId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email này đã được sử dụng");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserDuplicatePhone() {
        UUID adminId = UUID.randomUUID();
        var request = adminUserCreateRequest("john@test.com", "John", "STUDENT", "0934567890");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("john@test.com"))
                .thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0934567890"))
                .thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> userService.createUser(adminId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Số điện thoại này đã được sử dụng");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserInvalidRole() {
        UUID adminId = UUID.randomUUID();
        var request = adminUserCreateRequest("john@test.com", "John", "INVALID_ROLE", null);

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("john@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser(adminId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Vai trò không hợp lệ: INVALID_ROLE");
        verify(userRepository, never()).save(any());
    }

    // ── updateUser ───────────────────────────────────────────────────────────

    @Test
    void updateUserSuccess() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var existing = userEntity(userId, UserRole.STUDENT, UserStatus.ACTIVE);
        existing.setPhoneNumber("0934567890");
        var request = adminUserUpdateRequest("Updated Name", "new@test.com", "0987654321",
                "INSTRUCTOR", "DISABLED");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new@test.com"))
                .thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0987654321"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var expectedResponse = userResponse(userId);
        when(userMapper.toResponse(any(UserEntity.class))).thenReturn(expectedResponse);

        UserResponse result = userService.updateUser(adminId, userId, request);

        assertThat(result).isSameAs(expectedResponse);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity saved = userCaptor.getValue();
        assertThat(saved.getFullName()).isEqualTo("Updated Name");
        assertThat(saved.getEmail()).isEqualTo("new@test.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("0987654321");
        assertThat(saved.getRole()).isEqualTo(UserRole.INSTRUCTOR);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(saved.getDisabledAt()).isNotNull();
        assertThat(saved.getDisabledBy()).isEqualTo(adminId);

        verify(redisService).delete(RedisKeyConstants.ADMIN_USER_DETAIL + userId);
        verify(redisService).delete(RedisKeyConstants.USER_PROFILE + userId);
        verify(auditLogRepository).save(any(AuditLogEntity.class));
    }

    @Test
    void updateUserDuplicateEmail() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var existing = userEntity(userId, UserRole.STUDENT, UserStatus.ACTIVE);
        existing.setEmail("old@test.com");
        var request = adminUserUpdateRequest(null, "taken@test.com", null, null, null);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("taken@test.com"))
                .thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> userService.updateUser(adminId, userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email này đã được sử dụng");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserDuplicatePhone() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var existing = userEntity(userId, UserRole.STUDENT, UserStatus.ACTIVE);
        existing.setPhoneNumber("0934567890");
        var request = adminUserUpdateRequest(null, null, "0999999999", null, null);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(existing));
        when(userRepository.findByPhoneNumberAndDeletedAtIsNull("0999999999"))
                .thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> userService.updateUser(adminId, userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Số điện thoại này đã được sử dụng");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserSelfRoleChange() {
        UUID adminId = UUID.randomUUID();
        var existing = userEntity(adminId, UserRole.ADMIN, UserStatus.ACTIVE);
        var request = adminUserUpdateRequest(null, null, null, "STUDENT", null);

        when(userRepository.findByIdAndDeletedAtIsNull(adminId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.updateUser(adminId, adminId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quản trị viên không thể tự đổi vai trò của chính mình");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserLastAdminDowngrade() {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        var existing = userEntity(targetId, UserRole.ADMIN, UserStatus.ACTIVE);
        var request = adminUserUpdateRequest(null, null, null, "STUDENT", null);

        when(userRepository.findByIdAndDeletedAtIsNull(targetId)).thenReturn(Optional.of(existing));
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateUser(adminId, targetId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể hạ cấp quản trị viên cuối cùng");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserDeletedStatusChange() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var existing = userEntity(userId, UserRole.STUDENT, UserStatus.DELETED);
        var request = adminUserUpdateRequest(null, null, null, null, "ACTIVE");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.updateUser(adminId, userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể thay đổi trạng thái của tài khoản đã xóa");
        verify(userRepository, never()).save(any());
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPasswordSuccess() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId, UserRole.STUDENT, UserStatus.ACTIVE);
        var request = resetPasswordRequest("User requested reset");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_newpass");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageResponse result = userService.resetPassword(adminId, userId, request);

        assertThat(result.getMessage()).isEqualTo("Mật khẩu mới đã được gửi tới email của người dùng");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed_newpass");
        assertThat(captor.getValue().getPasswordChangedAt()).isNotNull();

        verify(emailService).sendAdminPasswordResetMail(eq(user.getEmail()), eq(user.getFullName()), anyString());
        verify(redisService).delete(RedisKeyConstants.USER_TOKENS + userId);
        verify(redisService).deleteRefreshToken(userId);
        verify(redisService).delete(RedisKeyConstants.ADMIN_USER_DETAIL + userId);
        verify(redisService).delete(RedisKeyConstants.USER_PROFILE + userId);
        verify(auditLogRepository).save(any(AuditLogEntity.class));
    }

    @Test
    void resetPasswordDeletedUser() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var user = userEntity(userId, UserRole.STUDENT, UserStatus.DELETED);
        var request = resetPasswordRequest("reason");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.resetPassword(adminId, userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể đặt lại mật khẩu cho tài khoản đã xóa");
        verify(userRepository, never()).save(any());
    }

    // ── batchCreateUsers ─────────────────────────────────────────────────────

    @Test
    void batchCreateUsersSuccess() {
        UUID adminId = UUID.randomUUID();
        var request1 = adminUserCreateRequest("user1@test.com", "User One", "STUDENT", "0111111111");
        var request2 = adminUserCreateRequest("user2@test.com", "User Two", "INSTRUCTOR", "0222222222");
        var responses = List.of(userResponse(UUID.randomUUID()), userResponse(UUID.randomUUID()));

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_batch");
        when(userRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(UserEntity.class)))
                .thenReturn(responses.get(0))
                .thenReturn(responses.get(1));

        List<UserResponse> result = userService.batchCreateUsers(adminId, List.of(request1, request2), null);

        assertThat(result).hasSize(2);

        ArgumentCaptor<List<UserEntity>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).saveAll(listCaptor.capture());
        List<UserEntity> savedList = listCaptor.getValue();
        assertThat(savedList).hasSize(2);
        assertThat(savedList.get(0).getEmail()).isEqualTo("user1@test.com");
        assertThat(savedList.get(1).getEmail()).isEqualTo("user2@test.com");
        assertThat(savedList.get(0).getPasswordHash()).isEqualTo("hashed_batch");
        assertThat(savedList.get(1).getPasswordHash()).isEqualTo("hashed_batch");

        verify(emailAsyncService).sendNewAccountMailAsync(eq("user1@test.com"), eq("User One"), anyString(), eq(null));
        verify(emailAsyncService).sendNewAccountMailAsync(eq("user2@test.com"), eq("User Two"), anyString(), eq(null));
        verify(auditLogRepository, times(2)).save(any(AuditLogEntity.class));
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private UserEntity userEntity(UUID id, UserRole role, UserStatus status) {
        var user = new UserEntity();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setFullName("Test User " + id);
        user.setPasswordHash("password");
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }

    private AdminUserCreateRequest adminUserCreateRequest(String email, String fullName,
                                                          String role, String phoneNumber) {
        var request = new AdminUserCreateRequest();
        request.setEmail(email);
        request.setFullName(fullName);
        request.setRole(role);
        request.setPhoneNumber(phoneNumber);
        request.setCourseId(UUID.randomUUID());
        return request;
    }

    private AdminUserUpdateRequest adminUserUpdateRequest(String fullName, String email,
                                                          String phoneNumber, String role, String status) {
        var request = new AdminUserUpdateRequest();
        request.setFullName(fullName);
        request.setEmail(email);
        request.setPhoneNumber(phoneNumber);
        request.setRole(role);
        request.setStatus(status);
        return request;
    }

    private AdminUserListRequest adminUserListRequest(String search, String role,
                                                      String status, String sortBy, String sortDir) {
        var request = new AdminUserListRequest();
        request.setSearch(search);
        request.setRole(role);
        request.setStatus(status);
        request.setSortBy(sortBy);
        request.setSortDir(sortDir);
        return request;
    }

    private ResetPasswordRequest resetPasswordRequest(String reason) {
        var request = new ResetPasswordRequest();
        request.setReason(reason);
        return request;
    }

    private Course courseEntity(UUID id, String title) {
        var c = new Course();
        c.setId(id);
        c.setTitle(title);
        return c;
    }

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
        resp.setFullName("Test User");
        resp.setRole("INSTRUCTOR");
        resp.setStatus("ACTIVE");
        return resp;
    }
}
