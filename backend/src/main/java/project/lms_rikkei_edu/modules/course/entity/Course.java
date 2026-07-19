package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "courses")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CourseCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 250)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CourseLevel level;

    @Column(name = "thumbnail_url", columnDefinition = "text")
    private String thumbnailUrl;

    @Convert(converter = project.lms_rikkei_edu.modules.course.converter.StringListJsonConverter.class)
    @Column(name = "learning_outcomes", columnDefinition = "text")
    @Builder.Default
    private List<String> learningOutcomes = new ArrayList<>();

    @Convert(converter = project.lms_rikkei_edu.modules.course.converter.StringListJsonConverter.class)
    @Column(name = "requirements", columnDefinition = "text")
    @Builder.Default
    private List<String> requirements = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "chat_enabled")
    @Builder.Default
    private Boolean chatEnabled = false;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "pending_update_at")
    private Instant pendingUpdateAt;

    // ── Hybrid draft fields (metadata chờ duyệt khi PUBLISHED) ──

    @Column(name = "draft_title", length = 200)
    private String draftTitle;

    @Column(name = "draft_description", columnDefinition = "text")
    private String draftDescription;

    @Column(name = "draft_thumbnail_url", columnDefinition = "text")
    private String draftThumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_level", length = 20)
    private CourseLevel draftLevel;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "draft_rejection_reason", columnDefinition = "text")
    private String draftRejectionReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<Chapter> chapters = new ArrayList<>();

    /** True nếu có bất kỳ nội dung draft nào đang chờ duyệt */
    public boolean isHasPendingDraft() {
        if (draftTitle != null || draftDescription != null
                || draftLevel != null || draftThumbnailUrl != null) return true;
        if (chapters == null) return false;
        return chapters.stream().anyMatch(ch ->
                Boolean.TRUE.equals(ch.getIsDraft()) ||
                Boolean.TRUE.equals(ch.getPendingDelete()) ||
                ch.getLessons().stream().anyMatch(l ->
                        Boolean.TRUE.equals(l.getIsDraft()) ||
                        Boolean.TRUE.equals(l.getPendingDelete()) ||
                        l.getDraftTitle() != null ||
                        l.getDraftContentText() != null
                )
        );
    }
}
