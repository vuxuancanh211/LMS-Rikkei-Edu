package project.lms_rikkei_edu.modules.quiz.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "quiz_attempt_answers")
public class QuizAttemptAnswerEntity {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    // quiz_questions.id cho quiz STATIC (bản clone bất biến);
    // bank_questions.id cho quiz RANDOM_DRAW (câu hỏi rút thẳng từ ngân hàng, không clone) —
    // không có FK ràng buộc vì cột này tham chiếu 1 trong 2 bảng tùy loại quiz.
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    // Danh sách quiz_options.id học viên đã chọn
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_option_ids", columnDefinition = "jsonb")
    private List<UUID> selectedOptionIds;

    // Tính lúc chấm điểm, lưu luôn vào DB — không tính lại sau
    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "answered_at")
    private OffsetDateTime answeredAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
