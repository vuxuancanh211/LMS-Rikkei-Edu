package project.lms_rikkei_edu.modules.assignment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "assignment_submissions")
public class AssignmentSubmissionEntity {

    @Id
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "is_late")
    private Boolean isLate;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(columnDefinition = "text")
    private String feedback;

    @Column(name = "graded_by")
    private UUID gradedBy;

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;

    @Column(name = "score_published_at")
    private OffsetDateTime scorePublishedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
