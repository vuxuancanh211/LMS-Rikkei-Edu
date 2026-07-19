package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "bank_options")
public class BankOptionEntity {

    @Id
    private UUID id;

    @Column(name = "bank_question_id", nullable = false)
    private UUID bankQuestionId;

    @Column(name = "option_text", columnDefinition = "text", nullable = false)
    private String optionText;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "order_index")
    private Integer orderIndex;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
