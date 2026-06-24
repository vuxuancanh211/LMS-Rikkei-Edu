package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSnapshotDto {
    private String title;
    private String description;
    private String level;
    private String thumbnailUrl;
    private String categoryName;
    private List<ChapterSnap> chapters;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterSnap {
        private String title;
        private Integer orderIndex;
        private List<LessonSnap> lessons;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LessonSnap {
        private String title;
        private String lessonType;
        private String contentText;
        private Integer durationSeconds;
        private Integer orderIndex;
        private List<ResourceSnap> resources;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceSnap {
        private String displayName;
        private String resourceType;
        private String mimeType;
        private Long fileSizeBytes;
    }
}
