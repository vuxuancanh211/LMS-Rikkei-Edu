package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CourseCategoryResponse {
    private UUID id;
    private String name;
    private String slug;
}
