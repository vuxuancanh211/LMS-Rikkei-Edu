package project.lms_rikkei_edu.modules.user.service;

import project.lms_rikkei_edu.common.dto.response.PagedResponse;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserCreateRequest;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserListRequest;
import project.lms_rikkei_edu.modules.user.dto.request.AdminUserUpdateRequest;
import project.lms_rikkei_edu.modules.user.dto.request.ResetPasswordRequest;
import project.lms_rikkei_edu.modules.user.dto.response.AdminUserDetailResponse;
import project.lms_rikkei_edu.modules.user.dto.response.MessageResponse;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    PagedResponse<UserResponse> getUsers(AdminUserListRequest request);

    AdminUserDetailResponse getUserDetail(UUID userId);

    UserResponse createUser(UUID adminId, AdminUserCreateRequest request);

    UserResponse updateUser(UUID adminId, UUID userId, AdminUserUpdateRequest request);

    MessageResponse deleteUser(UUID adminId, UUID userId);

    MessageResponse resetPassword(UUID adminId, UUID userId, ResetPasswordRequest request);
}
