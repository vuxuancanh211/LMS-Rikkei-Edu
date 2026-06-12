package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CourseResponse {
    private UUID id;
    private String title;
    private String slug;
    private CourseStatus status;
    private CourseLevel level;
    private String thumbnailUrl;
    private String description;
    private Boolean chatEnabled;
    private CourseCategoryResponse category;
    private Instant createdAt;
    private Instant updatedAt;
}
