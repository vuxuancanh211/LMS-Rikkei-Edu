package project.lms_rikkei_edu.modules.forum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "forum_reports")
public class ForumReportEntity {

    @Id
    private UUID id;

    @Column(name = "target_type", length = 10)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(length = 20)
    private String reason;

    @Column(length = 500)
    private String description;

    @Column(length = 20)
    private String status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
