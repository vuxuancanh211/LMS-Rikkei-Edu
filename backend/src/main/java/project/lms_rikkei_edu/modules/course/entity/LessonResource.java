package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_resources")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 20)
    private ResourceType resourceType;

    @Column(name = "is_downloadable")
    @Builder.Default
    private Boolean isDownloadable = true;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    /** true khi resource được thêm trong lần cập nhật đang chờ duyệt */
    @Column(name = "is_new_in_update")
    @Builder.Default
    private Boolean isNewInUpdate = false;

    /** true khi instructor muốn xóa resource của khóa PUBLISHED — chờ admin duyệt */
    @Column(name = "pending_delete")
    @Builder.Default
    private Boolean pendingDelete = false;
}
