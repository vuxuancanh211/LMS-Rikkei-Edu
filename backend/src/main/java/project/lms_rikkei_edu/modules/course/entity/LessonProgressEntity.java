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
@Table(name = "lesson_progress")
public class LessonProgressEntity {

    @Id
    private UUID id;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "watched_percentage")
    private BigDecimal watchedPercentage;

    @Column(name = "last_playback_position")
    private Integer lastPlaybackPosition;

    @Column(name = "document_view_seconds")
    private Integer documentViewSeconds;

    @Column(name = "lesson_percentage")
    private BigDecimal lessonPercentage;

    @Column(name = "first_accessed_at")
    private Instant firstAccessedAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
