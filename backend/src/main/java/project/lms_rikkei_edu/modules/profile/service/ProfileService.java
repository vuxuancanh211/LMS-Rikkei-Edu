package project.lms_rikkei_edu.modules.profile.service;

import org.springframework.web.multipart.MultipartFile;
import project.lms_rikkei_edu.modules.profile.dto.request.ChangePasswordRequest;
import project.lms_rikkei_edu.modules.profile.dto.request.ProfileUpdateRequest;
import project.lms_rikkei_edu.modules.profile.dto.response.ProfileResponse;

import java.util.UUID;

public interface ProfileService {

    ProfileResponse getProfile(UUID userId);

    ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request);

    ProfileResponse changePassword(UUID userId, ChangePasswordRequest request);

    ProfileResponse uploadAvatar(UUID userId, MultipartFile file);
}
