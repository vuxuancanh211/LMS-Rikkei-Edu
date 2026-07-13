package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "quiz_options")
public class QuizOptionEntity {

    @Id
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "option_text", columnDefinition = "text", nullable = false)
    private String optionText;

    // KHÔNG expose trường này ra client khi đang thi
    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "order_index")
    private Integer orderIndex;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
