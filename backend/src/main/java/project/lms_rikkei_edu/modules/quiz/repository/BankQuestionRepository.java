package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.quiz.entity.BankQuestionEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;

import java.util.List;
import java.util.UUID;

public interface BankQuestionRepository extends JpaRepository<BankQuestionEntity, UUID> {

    List<BankQuestionEntity> findByCourseId(UUID courseId);

    List<BankQuestionEntity> findByCourseIdAndStatus(UUID courseId, QuestionStatus status);

    List<BankQuestionEntity> findByCourseIdAndStatusAndDifficulty(
            UUID courseId, QuestionStatus status, QuestionDifficulty difficulty);

    List<BankQuestionEntity> findByCourseIdAndStatusAndSubjectTag(
            UUID courseId, QuestionStatus status, String subjectTag);

    long countByCourseIdAndStatus(UUID courseId, QuestionStatus status);

    long countByCourseIdAndStatusAndDifficulty(
            UUID courseId, QuestionStatus status, QuestionDifficulty difficulty);

    // Kiểm tra trùng exact match — dùng khi import
    boolean existsByCourseIdAndQuestionText(UUID courseId, String questionText);

    // Kiểm tra câu hỏi còn đang được tham chiếu bởi quiz_questions không
    @Query("SELECT COUNT(q) > 0 FROM QuizQuestionEntity q WHERE q.bankQuestionId = :bankQuestionId")
    boolean hasQuizReference(@Param("bankQuestionId") UUID bankQuestionId);

    // Rút ngẫu nhiên theo độ khó — dùng cho Type 3 BY_DIFFICULTY
    @Query(value = """
            SELECT * FROM bank_questions
            WHERE course_id = :courseId
              AND status = 'ACTIVE'
              AND difficulty = :difficulty
              AND (:tag IS NULL OR subject_tag = :tag)
            ORDER BY random()
            LIMIT :count
            """, nativeQuery = true)
    List<BankQuestionEntity> randomByDifficulty(
            @Param("courseId") UUID courseId,
            @Param("difficulty") String difficulty,
            @Param("tag") String subjectTag,
            @Param("count") int count);

    // Rút ngẫu nhiên hoàn toàn — dùng cho Type 3 FULLY_RANDOM
    @Query(value = """
            SELECT * FROM bank_questions
            WHERE course_id = :courseId
              AND status = 'ACTIVE'
              AND (:tag IS NULL OR subject_tag = :tag)
            ORDER BY random()
            LIMIT :count
            """, nativeQuery = true)
    List<BankQuestionEntity> randomFully(
            @Param("courseId") UUID courseId,
            @Param("tag") String subjectTag,
            @Param("count") int count);
}
