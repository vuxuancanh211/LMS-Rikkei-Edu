package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptAnswerEntity;

import java.util.List;
import java.util.UUID;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswerEntity, UUID> {

    List<QuizAttemptAnswerEntity> findByAttemptId(UUID attemptId);

    // Thống kê tỉ lệ đúng/sai per quiz_question — dùng cho stats giảng viên
    @Query("""
            SELECT a.questionId,
                   COUNT(a) AS total,
                   SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END) AS correctCount
            FROM QuizAttemptAnswerEntity a
            WHERE a.questionId IN :questionIds
            GROUP BY a.questionId
            """)
    List<Object[]> countCorrectByQuestionIds(@Param("questionIds") List<UUID> questionIds);

    // Lịch sử câu sai của học viên — trace qua bank_question_id
    @Query("""
            SELECT a FROM QuizAttemptAnswerEntity a
            JOIN QuizAttemptEntity att ON att.id = a.attemptId
            JOIN QuizQuestionEntity q ON q.id = a.questionId
            WHERE att.studentId = :studentId
              AND a.isCorrect = false
              AND q.bankQuestionId IS NOT NULL
            ORDER BY a.answeredAt DESC
            """)
    List<QuizAttemptAnswerEntity> findWrongAnswersByStudent(@Param("studentId") UUID studentId);
}
