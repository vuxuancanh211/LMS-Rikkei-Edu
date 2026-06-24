package project.lms_rikkei_edu.modules.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserCreateRequest {

    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(max = 200, message = "Họ tên không được vượt quá 200 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Vui lòng chọn vai trò")
    private String role;

    @Pattern(regexp = "^(0[3-9][0-9]{8,9})$", message = "Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0)")
    private String phoneNumber;
}
