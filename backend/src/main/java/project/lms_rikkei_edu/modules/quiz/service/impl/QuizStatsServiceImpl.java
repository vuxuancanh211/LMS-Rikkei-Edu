package project.lms_rikkei_edu.modules.quiz.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptEntity;
import project.lms_rikkei_edu.modules.quiz.entity.QuizEntity;
import project.lms_rikkei_edu.modules.quiz.entity.QuizQuestionEntity;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;
import project.lms_rikkei_edu.modules.quiz.repository.*;
import project.lms_rikkei_edu.modules.quiz.service.QuizStatsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizStatsServiceImpl implements QuizStatsService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository answerRepository;
    private final QuizQuestionRepository questionRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    // ── Instructor: quiz tổng quan ────────────────────────────────────────────

    @Override
    public QuizStatsResponse getQuizStats(UUID courseId, UUID quizId) {
        QuizEntity quiz = findQuiz(courseId, quizId);

        long totalAttempts = attemptRepository.countByQuizIdAndStatus(quizId, AttemptStatus.GRADED);
        long uniqueStudents = attemptRepository.countDistinctStudentsByQuizId(quizId);
        long passCount = attemptRepository.countPassedByQuizId(quizId);

        BigDecimal avgScore = toDecimal(attemptRepository.avgScoreByQuizId(quizId));
        BigDecimal avgPct = toDecimal(attemptRepository.avgScorePercentageByQuizId(quizId));
        BigDecimal avgTime = toDecimal(attemptRepository.avgTimeSpentByQuizId(quizId));

        BigDecimal passRate = totalAttempts > 0
                ? BigDecimal.valueOf(passCount)
                        .divide(BigDecimal.valueOf(totalAttempts), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Per-question stats — chỉ cho STATIC / SHUFFLED_POOL (có quiz_questions)
        List<QuizQuestionStatsResponse> questionStats = buildQuestionStats(quizId);

        return QuizStatsResponse.builder()
                .quizId(quizId)
                .quizTitle(quiz.getTitle())
                .totalAttempts(totalAttempts)
                .uniqueStudents(uniqueStudents)
                .avgScore(avgScore)
                .avgScorePercentage(avgPct)
                .passRate(passRate)
                .passCount(passCount)
                .avgTimeSpentSeconds(avgTime)
                .questionStats(questionStats)
                .build();
    }

    // ── Student: lịch sử lần thi ──────────────────────────────────────────────

    @Override
    public List<AttemptHistoryEntry> getStudentAttemptHistory(UUID courseId, UUID quizId, UUID studentId) {
        findQuiz(courseId, quizId);
        checkEnrollment(courseId, studentId);
        return attemptRepository
                .findByQuizIdAndStudentIdOrderByAttemptNumber(quizId, studentId)
                .stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    // ── Student: tiến độ toàn khóa ────────────────────────────────────────────

    @Override
    public List<StudentQuizProgressEntry> getStudentCourseProgress(UUID courseId, UUID studentId) {
        checkEnrollment(courseId, studentId);
        List<QuizEntity> quizzes = quizRepository.findByCourseId(courseId);
        return quizzes.stream()
                .map(quiz -> buildProgressEntry(quiz, studentId))
                .toList();
    }

    // ── Instructor: tất cả attempt của quiz ───────────────────────────────────

    @Override
    public List<AttemptHistoryEntry> getAllAttemptsForQuiz(UUID courseId, UUID quizId) {
        findQuiz(courseId, quizId);
        return attemptRepository.findByQuizId(quizId)
                .stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private QuizEntity findQuiz(UUID courseId, UUID quizId) {
        return quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException("Quiz không tồn tại", HttpStatus.NOT_FOUND));
    }

    private void checkEnrollment(UUID courseId, UUID studentId) {
        if (!courseEnrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId))
            throw new BusinessException("Bạn chưa đăng ký khóa học này");
    }

    private List<QuizQuestionStatsResponse> buildQuestionStats(UUID quizId) {
        List<QuizQuestionEntity> questions = questionRepository.findByQuizIdOrderByOrderIndex(quizId);
        if (questions.isEmpty()) return List.of();

        List<UUID> questionIds = questions.stream().map(QuizQuestionEntity::getId).toList();
        List<Object[]> rows = answerRepository.countCorrectByQuestionIds(questionIds);

        // Map questionId → [total, correctCount]
        Map<UUID, long[]> statsMap = rows.stream().collect(Collectors.toMap(
                r -> (UUID) r[0],
                r -> new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()}
        ));

        return questions.stream().map(q -> {
            long[] counts = statsMap.getOrDefault(q.getId(), new long[]{0L, 0L});
            long total = counts[0];
            long correct = counts[1];
            BigDecimal rate = total > 0
                    ? BigDecimal.valueOf(correct)
                            .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return QuizQuestionStatsResponse.builder()
                    .questionId(q.getId())
                    .questionText(q.getQuestionText())
                    .questionType(q.getQuestionType())
                    .difficulty(q.getDifficulty())
                    .totalAnswers(total)
                    .correctCount(correct)
                    .correctRate(rate)
                    .build();
        }).toList();
    }

    private StudentQuizProgressEntry buildProgressEntry(QuizEntity quiz, UUID studentId) {
        long used = attemptRepository.countByQuizIdAndStudentId(quiz.getId(), studentId);
        Integer max = quiz.getMaxAttempts(); // null = không giới hạn

        var best = attemptRepository.findBestAttemptByQuizIdAndStudentId(quiz.getId(), studentId);
        boolean passed = best.map(a -> Boolean.TRUE.equals(a.getIsPassed())).orElse(false);
        BigDecimal bestScore = best.map(QuizAttemptEntity::getScore).orElse(null);
        BigDecimal bestPct = best.map(QuizAttemptEntity::getScorePercentage).orElse(null);

        // Cooldown check
        boolean cooldownPassed = true;
        if (used > 0) {
            var latest = attemptRepository.findLatestByQuizIdAndStudentId(quiz.getId(), studentId);
            cooldownPassed = latest.map(a -> {
                if (a.getSubmittedAt() == null) return false; // still in progress
                int cooldown = quiz.getCooldownMinutes() != null ? quiz.getCooldownMinutes() : 20;
                return OffsetDateTime.now().isAfter(a.getSubmittedAt().plusMinutes(cooldown));
            }).orElse(true);
        }

        boolean canRetry = (max == null || used < max) && cooldownPassed;

        return StudentQuizProgressEntry.builder()
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .quizType(quiz.getQuizType())
                .quizStatus(quiz.getStatus())
                .maxAttempts(max)
                .attemptsUsed((int) used)
                .passed(passed)
                .bestScore(bestScore)
                .bestScorePercentage(bestPct)
                .canRetry(canRetry)
                .build();
    }

    private AttemptHistoryEntry toHistoryEntry(QuizAttemptEntity a) {
        return AttemptHistoryEntry.builder()
                .attemptId(a.getId())
                .attemptNumber(a.getAttemptNumber())
                .status(a.getStatus())
                .score(a.getScore())
                .scorePercentage(a.getScorePercentage())
                .isPassed(a.getIsPassed())
                .correctCount(a.getCorrectCount() != null ? a.getCorrectCount() : 0)
                .incorrectCount(a.getIncorrectCount() != null ? a.getIncorrectCount() : 0)
                .unansweredCount(a.getUnansweredCount() != null ? a.getUnansweredCount() : 0)
                .timeSpentSeconds(a.getTimeSpentSeconds())
                .autoSubmitted(Boolean.TRUE.equals(a.getAutoSubmitted()))
                .violationCount(a.getViolationCount() != null ? a.getViolationCount() : 0)
                .startedAt(a.getStartedAt())
                .submittedAt(a.getSubmittedAt())
                .build();
    }

    private BigDecimal toDecimal(Double d) {
        return d != null ? BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
}
