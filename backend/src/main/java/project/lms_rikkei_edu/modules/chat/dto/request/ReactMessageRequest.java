package project.lms_rikkei_edu.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReactMessageRequest {
    @NotBlank(message = "Emoji không được để trống")
    @Size(max = 10)
    private String emoji;
}
