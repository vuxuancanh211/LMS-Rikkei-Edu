package project.lms_rikkei_edu.modules.profile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.profile.dto.request.ChangePasswordRequest;
import project.lms_rikkei_edu.modules.profile.dto.request.ProfileUpdateRequest;
import project.lms_rikkei_edu.modules.profile.dto.response.ProfileResponse;
import project.lms_rikkei_edu.modules.profile.service.ProfileService;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> getProfile() {
        var userId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        var userId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        var userId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
        return ResponseEntity.ok(profileService.changePassword(userId, request));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> uploadAvatar(@RequestPart("file") MultipartFile file) {
        var userId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
        return ResponseEntity.ok(profileService.uploadAvatar(userId, file));
    }
}
