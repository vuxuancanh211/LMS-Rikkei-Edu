package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.GenerationStep;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ai_question_generation_jobs")
public class AiQuestionGenerationJob {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private GenerationStep step;

    /** Serialized {@code AiGenerateQuestionsResponse} — populated once step=DONE. */
    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (step == null) step = GenerationStep.RETRIEVING_CONTEXT;
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
