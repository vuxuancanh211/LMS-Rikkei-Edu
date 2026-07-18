package project.lms_rikkei_edu.modules.user.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class AdminUserDetailResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String role;
    private String status;
    private String phoneNumber;
    private String avatarUrl;
    private LocalDate birthDate;
    private String gender;
    private String bio;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
