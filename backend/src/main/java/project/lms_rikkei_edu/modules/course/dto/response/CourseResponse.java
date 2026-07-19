package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/* Getter+Setter+no-args ctor cần cho Jackson deserialize lại từ Redis khi
   cache "course-list"/"course-detail" — @Builder-only (không setter/no-args
   ctor) không có Creator nào để Jackson dựng lại object trên đường đọc cache. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {
    private UUID id;
    private String title;
    private String slug;
    private CourseStatus status;
    private CourseLevel level;
    private String thumbnailUrl;
    private String description;
    private Boolean chatEnabled;
    private UUID instructorId;
    @Setter
    private String instructorName;
    private CourseCategoryResponse category;
    private Instant submittedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<String> learningOutcomes;
    @Setter
    private Integer studentCount;
}
