package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_approval_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseApprovalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "action", length = 20)
    private String action;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;
}
