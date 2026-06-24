package project.lms_rikkei_edu.modules.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AdminUserUpdateRequest {

    @Size(max = 200, message = "Họ tên không được vượt quá 200 ký tự")
    private String fullName;

    @Email(message = "Email không đúng định dạng")
    private String email;

    @Pattern(regexp = "^(0[3-9][0-9]{8,9})$", message = "Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0)")
    private String phoneNumber;

    private String avatarUrl;

    private LocalDate birthDate;

    private String gender;

    private String bio;

    private String role;

    private String status;
}
