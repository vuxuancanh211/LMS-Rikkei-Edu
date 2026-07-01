package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;

import java.util.UUID;

@Getter
@Setter
public class CreateCourseRequest {

    @NotBlank
    @Size(min = 5, max = 200)
    private String title;

    @Size(max = 5000)
    private String description;

    private UUID categoryId;

    private CourseLevel level;

    private String thumbnailUrl;

    private Boolean chatEnabled = false;
}
