package project.lms_rikkei_edu.modules.user.service;

import project.lms_rikkei_edu.common.dto.response.PagedResponse;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserCreateRequest;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserListRequest;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserUpdateRequest;
import project.lms_rikkei_edu.modules.user.dto.request.ResetPasswordRequest;
import project.lms_rikkei_edu.modules.user.dto.response.AdminUserDetailResponse;
import project.lms_rikkei_edu.modules.user.dto.response.MessageResponse;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    PagedResponse<UserResponse> getUsers(AdminUserListRequest request);

    AdminUserDetailResponse getUserDetail(UUID userId);

    UserResponse createUser(UUID adminId, AdminUserCreateRequest request);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    UserResponse updateUser(UUID adminId, UUID userId, AdminUserUpdateRequest request);

    MessageResponse resetPassword(UUID adminId, UUID userId, ResetPasswordRequest request);

    List<UserResponse> batchCreateUsers(UUID adminId, List<AdminUserCreateRequest> requests, String courseTitle);
}
