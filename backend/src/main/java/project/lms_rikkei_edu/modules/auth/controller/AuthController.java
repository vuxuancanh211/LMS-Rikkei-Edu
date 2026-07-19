package project.lms_rikkei_edu.modules.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.modules.auth.dto.request.ForgotPasswordRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.LoginRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.RefreshTokenRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.ResetPasswordRequest;
import project.lms_rikkei_edu.modules.auth.dto.response.ActivateAccountResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.ForgotPasswordResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.LoginResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.LogoutResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.RefreshTokenResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.ResetPasswordResponse;
import project.lms_rikkei_edu.modules.auth.service.AuthService;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpServletRequest) {
        String secretHeader = httpServletRequest.getHeader("X-Auth-Secret");
        if (secretHeader != null && !secretHeader.isBlank() && request.getPassword() != null && request.getPassword().matches("^\\*+$")) {
            request.setPassword(secretHeader);
        }
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(authService.getMe());
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LogoutResponse> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody(required = false) RefreshTokenRequest request) {
        return ResponseEntity.ok(request != null ? authService.logout(authorizationHeader, request) : authService.logout(authorizationHeader));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }
    
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request, jakarta.servlet.http.HttpServletRequest httpServletRequest) {
        String newSecret = httpServletRequest.getHeader("X-Auth-Secret-New");
        String confirmSecret = httpServletRequest.getHeader("X-Auth-Secret-Confirm");
        if (newSecret != null && !newSecret.isBlank() && request.getNewPassword() != null && request.getNewPassword().matches("^\\*+$")) {
            request.setNewPassword(newSecret);
        }
        if (confirmSecret != null && !confirmSecret.isBlank() && request.getConfirmPassword() != null && request.getConfirmPassword().matches("^\\*+$")) {
            request.setConfirmPassword(confirmSecret);
        }
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @GetMapping("/activate")
    public ResponseEntity<ActivateAccountResponse> activateAccount(@RequestParam String token) {
        return ResponseEntity.ok(authService.activateAccount(token));
    }
}
