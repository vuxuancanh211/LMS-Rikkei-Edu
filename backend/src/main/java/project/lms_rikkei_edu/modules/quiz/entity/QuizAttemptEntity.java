package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "quiz_attempts")
public class QuizAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AttemptStatus status;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "score_percentage", precision = 5, scale = 2)
    private BigDecimal scorePercentage;

    @Column(name = "is_passed")
    private Boolean isPassed;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "incorrect_count")
    private Integer incorrectCount;

    @Column(name = "unanswered_count")
    private Integer unansweredCount;

    // Snapshot thứ tự câu hỏi khi shuffle — dùng để hiển thị lại đúng thứ tự
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_order", columnDefinition = "jsonb")
    private List<UUID> questionOrder;

    // Snapshot thứ tự đáp án khi shuffle per câu
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_order", columnDefinition = "jsonb")
    private List<UUID> optionOrder;

    @Column(name = "auto_submitted")
    private Boolean autoSubmitted = false;

    @Column(name = "proctoring_enabled")
    private Boolean proctoringEnabled = false;

    @Column(name = "violation_count")
    private Integer violationCount = 0;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = AttemptStatus.IN_PROGRESS;
        if (violationCount == null) violationCount = 0;
        startedAt = OffsetDateTime.now();
    }
}
