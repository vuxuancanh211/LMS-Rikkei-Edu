package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.enums.VideoStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lessons")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LessonType type;

    @Column(name = "content_text", columnDefinition = "text")
    private String contentText;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "is_preview")
    @Builder.Default
    private Boolean isPreview = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_status", length = 20)
    private VideoStatus videoStatus;

    @Column(name = "video_s3_key", length = 500)
    private String videoS3Key;

    @Column(name = "hls_manifest_url", columnDefinition = "text")
    private String hlsManifestUrl;

    /** Chỉ dùng khi type == QUIZ — trỏ tới quiz đang gắn với lesson này (unique, tối đa 1 lesson/quiz). */
    @Column(name = "quiz_id")
    private UUID quizId;

    /** true = lesson này vừa được tạo trong published course, chưa được admin duyệt */
    @Column(name = "is_draft", nullable = false)
    @Builder.Default
    private Boolean isDraft = false;

    /** true = instructor muốn xóa lesson này, chờ admin duyệt */
    @Column(name = "pending_delete", nullable = false)
    @Builder.Default
    private Boolean pendingDelete = false;

    /** Tiêu đề draft chờ duyệt (lesson live, title thay đổi) */
    @Column(name = "draft_title", length = 200)
    private String draftTitle;

    /** Nội dung draft chờ duyệt */
    @Column(name = "draft_content_text", columnDefinition = "text")
    private String draftContentText;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Soft-delete — set khi admin duyệt xóa bài, thay vì xóa cứng, để giữ tài liệu cho rollback. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<LessonResource> resources = new ArrayList<>();
}
