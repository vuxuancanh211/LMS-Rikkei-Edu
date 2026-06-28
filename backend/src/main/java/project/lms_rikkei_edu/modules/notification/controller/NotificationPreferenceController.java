package project.lms_rikkei_edu.modules.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.notification.dto.request.UpdateNotificationPreferenceRequest;
import project.lms_rikkei_edu.modules.notification.dto.response.NotificationPreferenceResponse;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/preferences")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences() {
        UUID userId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));
        return ResponseEntity.ok(preferenceService.getPreferences(userId));
    }

    @PutMapping("/{type}")
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(
            @PathVariable String type,
            @RequestBody UpdateNotificationPreferenceRequest request
    ) {
        UUID userId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User is not authenticated"));
        return ResponseEntity.ok(preferenceService.updatePreference(userId, type, request));
    }
}
