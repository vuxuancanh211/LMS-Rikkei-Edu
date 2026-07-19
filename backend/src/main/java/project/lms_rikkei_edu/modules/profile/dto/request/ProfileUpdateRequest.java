package project.lms_rikkei_edu.modules.profile.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProfileUpdateRequest {

    private String fullName;
    private String phoneNumber;
    private LocalDate birthDate;
    private String gender;
    private String bio;
}
