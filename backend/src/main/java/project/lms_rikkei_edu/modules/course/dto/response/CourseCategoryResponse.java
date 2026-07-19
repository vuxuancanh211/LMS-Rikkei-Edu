package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/* Getter+Setter+no-args ctor cần cho Jackson deserialize lại từ Redis cache "course-detail"/"course-list". */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCategoryResponse {
    private UUID id;
    private String name;
    private String slug;
}
