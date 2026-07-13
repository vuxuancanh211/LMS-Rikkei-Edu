package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import project.lms_rikkei_edu.modules.quiz.enums.QuizStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuizType;
import project.lms_rikkei_edu.modules.quiz.enums.RandomMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "quizzes")
public class QuizEntity {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_type", length = 20)
    private QuizType quizType;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "max_attempts")
    private Integer maxAttempts = 3;

    @Column(name = "pass_score", precision = 5, scale = 2)
    private BigDecimal passScore;

    @Column(name = "shuffle_questions")
    private Boolean shuffleQuestions = false;

    @Column(name = "shuffle_options")
    private Boolean shuffleOptions = false;

    @Column(name = "proctoring_enabled")
    private Boolean proctoringEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QuizStatus status;

    // Random Draw config
    @Enumerated(EnumType.STRING)
    @Column(name = "random_mode", length = 20)
    private RandomMode randomMode;

    // {"easy": 3, "medium": 3, "hard": 4} — chỉ dùng khi BY_DIFFICULTY
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "difficulty_config", columnDefinition = "jsonb")
    private Map<String, Integer> difficultyConfig;

    @Column(name = "random_total_count")
    private Integer randomTotalCount;

    @Column(name = "subject_tag_filter", length = 100)
    private String subjectTagFilter;

    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes = 20;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = QuizStatus.DRAFT;
        createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
