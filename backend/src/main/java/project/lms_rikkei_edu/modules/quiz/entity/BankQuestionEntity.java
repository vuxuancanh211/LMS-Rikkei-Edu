package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "bank_questions")
public class BankQuestionEntity {

    @Id
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "subject_tag", length = 100)
    private String subjectTag;

    @Column(name = "question_text", columnDefinition = "text", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 20)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private QuestionDifficulty difficulty;

    @Column(precision = 5, scale = 2)
    private BigDecimal points;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QuestionStatus status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = QuestionStatus.ACTIVE;
        createdAt = OffsetDateTime.now();
    }
}
