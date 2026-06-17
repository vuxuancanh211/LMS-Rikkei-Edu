package project.lms_rikkei_edu.modules.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.user.dto.response.UserResponse;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserResponse user;
}
