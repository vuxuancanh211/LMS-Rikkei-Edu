package project.lms_rikkei_edu.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageRequest {
    @NotBlank(message = "Nội dung không được để trống")
    private String content;
}
