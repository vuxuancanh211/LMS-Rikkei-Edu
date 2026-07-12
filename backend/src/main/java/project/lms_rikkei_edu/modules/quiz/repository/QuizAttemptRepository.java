package project.lms_rikkei_edu.modules.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptEntity;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, UUID> {

    List<QuizAttemptEntity> findByQuizIdAndStudentIdOrderByAttemptNumber(UUID quizId, UUID studentId);

    long countByQuizIdAndStudentId(UUID quizId, UUID studentId);

    Optional<QuizAttemptEntity> findByQuizIdAndStudentIdAndStatus(
            UUID quizId, UUID studentId, AttemptStatus status);

    List<QuizAttemptEntity> findByQuizId(UUID quizId);

    // Batch — tải toàn bộ attempt của 1 học viên cho nhiều quiz trong 1 query, dùng để tính tiến độ
    // cả khóa (xem QuizStatsServiceImpl#getStudentCourseProgress) thay vì query riêng cho từng quiz.
    List<QuizAttemptEntity> findByQuizIdInAndStudentId(List<UUID> quizIds, UUID studentId);

    // Lần thi gần nhất của học viên — dùng để kiểm tra cooldown
    @Query("""
            SELECT a FROM QuizAttemptEntity a
            WHERE a.quizId = :quizId AND a.studentId = :studentId
            ORDER BY a.startedAt DESC
            LIMIT 1
            """)
    Optional<QuizAttemptEntity> findLatestByQuizIdAndStudentId(
            @Param("quizId") UUID quizId,
            @Param("studentId") UUID studentId);

    // Tìm attempt IN_PROGRESS đã quá hạn — để auto-expire
    @Query("""
            SELECT a FROM QuizAttemptEntity a
            JOIN QuizEntity q ON q.id = a.quizId
            WHERE a.status = 'IN_PROGRESS'
              AND a.startedAt < :expiredBefore
            """)
    List<QuizAttemptEntity> findExpiredAttempts(@Param("expiredBefore") OffsetDateTime expiredBefore);

    // Thống kê quiz — tổng attempt đã graded
    long countByQuizIdAndStatus(UUID quizId, AttemptStatus status);

    // Số học viên unique đã thi
    @Query("SELECT COUNT(DISTINCT a.studentId) FROM QuizAttemptEntity a WHERE a.quizId = :quizId AND a.status = 'GRADED'")
    long countDistinctStudentsByQuizId(@Param("quizId") UUID quizId);

    // Avg score percentage
    @Query("SELECT AVG(a.scorePercentage) FROM QuizAttemptEntity a WHERE a.quizId = :quizId AND a.status = 'GRADED'")
    Double avgScorePercentageByQuizId(@Param("quizId") UUID quizId);

    // Avg score
    @Query("SELECT AVG(a.score) FROM QuizAttemptEntity a WHERE a.quizId = :quizId AND a.status = 'GRADED'")
    Double avgScoreByQuizId(@Param("quizId") UUID quizId);

    // Số lượt pass
    @Query("SELECT COUNT(a) FROM QuizAttemptEntity a WHERE a.quizId = :quizId AND a.status = 'GRADED' AND a.isPassed = true")
    long countPassedByQuizId(@Param("quizId") UUID quizId);

    // Avg time spent
    @Query("SELECT AVG(a.timeSpentSeconds) FROM QuizAttemptEntity a WHERE a.quizId = :quizId AND a.status = 'GRADED'")
    Double avgTimeSpentByQuizId(@Param("quizId") UUID quizId);

    // Best attempt của student trên 1 quiz (theo scorePercentage cao nhất)
    @Query("""
            SELECT a FROM QuizAttemptEntity a
            WHERE a.quizId = :quizId AND a.studentId = :studentId AND a.status = 'GRADED'
            ORDER BY a.scorePercentage DESC
            LIMIT 1
            """)
    Optional<QuizAttemptEntity> findBestAttemptByQuizIdAndStudentId(
            @Param("quizId") UUID quizId, @Param("studentId") UUID studentId);
}
