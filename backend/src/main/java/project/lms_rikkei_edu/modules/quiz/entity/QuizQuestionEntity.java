package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "quiz_questions")
public class QuizQuestionEntity {

    @Id
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    // nullable — null nếu câu nhập thủ công, có giá trị nếu clone từ bank
    @Column(name = "bank_question_id")
    private UUID bankQuestionId;

    @Column(name = "question_text", columnDefinition = "text", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 20)
    private QuestionType questionType;

    // Clone từ bank lúc snapshot — dùng cho thống kê
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private QuestionDifficulty difficulty;

    @Column(name = "subject_tag", length = 100)
    private String subjectTag;

    @Column(precision = 5, scale = 2)
    private BigDecimal points;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(columnDefinition = "text")
    private String explanation;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
