package project.lms_rikkei_edu.modules.user.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AdminUserUpdateRequest {

    private String fullName;

    @Email(message = "Email is invalid")
    private String email;

    private String phoneNumber;

    private String avatarUrl;

    private LocalDate birthDate;

    private String gender;

    private String bio;

    private String role;

    private String status;
}
