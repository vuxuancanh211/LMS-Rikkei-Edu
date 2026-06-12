package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ChapterResponse {
    private UUID id;
    private String title;
    private String description;
    private Integer orderIndex;
    private List<LessonResponse> lessons;
    private Instant createdAt;
}
