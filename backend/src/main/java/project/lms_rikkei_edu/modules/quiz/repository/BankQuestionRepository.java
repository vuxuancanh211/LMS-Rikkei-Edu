package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.quiz.entity.BankQuestionEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;

import java.util.List;
import java.util.UUID;

public interface BankQuestionRepository extends JpaRepository<BankQuestionEntity, UUID> {

    // KHÔNG phân trang — vẫn cần cho pha text-match của hybrid search (BankQuestionServiceImpl#search)
    // và PickBankQuestionsModal (cần tải toàn bộ để chọn nhiều câu tùy ý). Giao diện danh sách chính
    // (tab Ngân hàng câu hỏi) dùng findByFilters phân trang bên dưới.
    List<BankQuestionEntity> findByCourseId(UUID courseId);

    // Phân trang cho tab Ngân hàng câu hỏi — tránh tải hết câu hỏi lên 1 lượt gây lag.
    // Gộp cả 3 filter (status/difficulty/subjectTag) vào 1 query duy nhất, null = bỏ qua filter đó —
    // thay cho logic if/else phân nhánh + lọc Java phía service (vốn phá vỡ phân trang đúng khi
    // kết hợp difficulty + subjectTag).
    @Query("""
            SELECT q FROM BankQuestionEntity q
            WHERE q.courseId = :courseId
              AND (:status IS NULL OR q.status = :status)
              AND (:difficulty IS NULL OR q.difficulty = :difficulty)
              AND (:subjectTag IS NULL OR q.subjectTag = :subjectTag)
            """)
    Page<BankQuestionEntity> findByFilters(
            @Param("courseId") UUID courseId,
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("subjectTag") String subjectTag,
            Pageable pageable);

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
