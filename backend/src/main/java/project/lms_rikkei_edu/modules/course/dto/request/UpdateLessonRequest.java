package project.lms_rikkei_edu.modules.course.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.LessonType;

@Getter
@Setter
public class UpdateLessonRequest {

    @Size(min = 3, max = 200)
    private String title;

    private LessonType type;

    @Size(max = 10000)
    private String contentText;

    private Boolean isPreview;
}
