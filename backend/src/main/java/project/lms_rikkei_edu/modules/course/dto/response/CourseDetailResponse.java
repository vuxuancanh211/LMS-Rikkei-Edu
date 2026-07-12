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

/* Getter+Setter+no-args ctor cần cho Jackson deserialize lại từ Redis cache "course-detail". */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailResponse {
    private UUID id;
    private String title;
    private String slug;
    private CourseStatus status;
    private CourseLevel level;
    private String description;
    private String thumbnailUrl;
    private Boolean chatEnabled;
    private String rejectionReason;
    private CourseCategoryResponse category;
    private List<ChapterResponse> chapters;
    private Instant submittedAt;
    private Instant publishedAt;
    private Instant pendingUpdateAt;
    private Instant createdAt;
    private Instant updatedAt;

    // ── Hybrid draft fields ──
    private boolean hasPendingDraft;
    private String draftTitle;
    private String draftDescription;
    private String draftThumbnailUrl;
    private CourseLevel draftLevel;
    private String changeSummary;
    private String draftRejectionReason;
}
