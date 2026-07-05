package project.lms_rikkei_edu.modules.course.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CourseDiffResponse {

    private MetaDiff metadata;
    private List<ChapterDiff> chapters;
    private List<ResourceDiff> resources;

    /** pendingVersionId — id của CourseVersion PENDING đang xét */
    private java.util.UUID pendingVersionId;
    private Integer pendingVersionNumber;
    private java.util.UUID approvedVersionId;
    private Integer approvedVersionNumber;

    @Getter
    @Builder
    public static class MetaDiff {
        private FieldDiff title;
        private FieldDiff description;
        private FieldDiff level;
        private FieldDiff thumbnailUrl;
    }

    @Getter
    @Builder
    public static class FieldDiff {
        private String oldValue;
        private String newValue;
        /** true nếu giá trị thay đổi */
        private boolean changed;
    }

    @Getter
    @Builder
    public static class ChapterDiff {
        /** ADDED | REMOVED | UNCHANGED | MODIFIED */
        private String action;
        private String title;
        private Integer orderIndex;
        private List<LessonDiff> lessons;
    }

    @Getter
    @Builder
    public static class LessonDiff {
        /** ADDED | REMOVED | UNCHANGED | MODIFIED */
        private String action;
        private String title;
        private String newTitle;
        private Integer orderIndex;
        private String lessonType;
    }

    @Getter
    @Builder
    public static class ResourceDiff {
        /** ADDED | REMOVED */
        private String action;
        private String displayName;
        private String resourceType;
        private String mimeType;
        private Long fileSizeBytes;
        /** Lesson chứa resource này */
        private String lessonTitle;
        private String chapterTitle;
    }
}
