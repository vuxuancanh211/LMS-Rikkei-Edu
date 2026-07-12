package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.ViolationType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "proctoring_violation_logs")
public class ProctoringViolationLogEntity {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", length = 30)
    private ViolationType violationType;

    // Thứ tự vi phạm trong attempt (1, 2, 3...)
    @Column(name = "violation_order")
    private Integer violationOrder;

    // WARNED | AUTO_SUBMITTED
    @Column(name = "action_taken", length = 20)
    private String actionTaken;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "client_timestamp")
    private OffsetDateTime clientTimestamp;

    @Column(name = "server_timestamp")
    private OffsetDateTime serverTimestamp;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        serverTimestamp = OffsetDateTime.now();
    }
}
