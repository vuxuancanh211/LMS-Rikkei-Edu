package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChapterRequest {

    @NotBlank
    @Size(min = 3, max = 200)
    private String title;

    @Size(max = 1000)
    private String description;
}
