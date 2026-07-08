package project.lms_rikkei_edu.modules.assignment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "assignments")
public class AssignmentEntity {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssignmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssignmentScope scope;

    private OffsetDateTime deadline;

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "allow_late_submission")
    private Boolean allowLateSubmission;

    @Column(name = "late_penalty_percent")
    private Integer latePenaltyPercent;

    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb;

    @Column(name = "allowed_file_types", columnDefinition = "jsonb")
    private String allowedFileTypes;

    @Column(name = "max_submissions")
    private Integer maxSubmissions;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
