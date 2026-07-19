package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UpdateCourseRequest {

    @Size(min = 5, max = 200)
    private String title;

    @Size(max = 20000)
    private String description;

    private UUID categoryId;

    private CourseLevel level;

    private String thumbnailUrl;

    private Boolean chatEnabled;

    @Size(max = 10)
    private List<@Size(max = 300) String> learningOutcomes;

    @Size(max = 10)
    private List<@Size(max = 300) String> requirements;
}
