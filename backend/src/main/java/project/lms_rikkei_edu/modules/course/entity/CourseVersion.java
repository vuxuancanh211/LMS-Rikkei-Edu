package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** PENDING | APPROVED | REJECTED */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** JSON snapshot của toàn bộ course tại thời điểm submit */
    @Column(name = "snapshot", columnDefinition = "text")
    private String snapshot;

    @Column(name = "change_summary", columnDefinition = "text")
    private String changeSummary;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
