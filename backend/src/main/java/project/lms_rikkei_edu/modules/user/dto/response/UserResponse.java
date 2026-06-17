package project.lms_rikkei_edu.modules.user.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UserResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String role;
    private String status;
    private String phoneNumber;
    private String avatarUrl;
}
