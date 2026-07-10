package project.lms_rikkei_edu.modules.assignment.entity;

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
@Table(name = "assignment_groups")
public class AssignmentGroupEntity {

    @Id
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;
}
