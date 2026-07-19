package project.lms_rikkei_edu.modules.auth.service;

import project.lms_rikkei_edu.modules.auth.dto.request.LoginRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.ForgotPasswordRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.RefreshTokenRequest;
import project.lms_rikkei_edu.modules.auth.dto.request.ResetPasswordRequest;
import project.lms_rikkei_edu.modules.auth.dto.response.ActivateAccountResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.ForgotPasswordResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.LoginResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.LogoutResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.RefreshTokenResponse;
import project.lms_rikkei_edu.modules.auth.dto.response.ResetPasswordResponse;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    UserResponse getMe();

    LogoutResponse logout(String authorizationHeader);

    LogoutResponse logout(String authorizationHeader, RefreshTokenRequest request);

    RefreshTokenResponse refresh(RefreshTokenRequest request);

    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    ResetPasswordResponse resetPassword(ResetPasswordRequest request);

    ActivateAccountResponse activateAccount(String token);
}
