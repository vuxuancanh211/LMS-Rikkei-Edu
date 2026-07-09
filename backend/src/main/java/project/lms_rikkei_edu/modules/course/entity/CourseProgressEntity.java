package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "course_progress")
public class CourseProgressEntity {

    @Id
    private UUID id;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "completed_lessons_count")
    private Integer completedLessonsCount;

    @Column(name = "total_lessons_count")
    private Integer totalLessonsCount;

    @Column(name = "overall_percentage")
    private BigDecimal overallPercentage;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
