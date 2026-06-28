package project.lms_rikkei_edu.modules.quiz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptEntity;
import project.lms_rikkei_edu.modules.quiz.entity.QuizEntity;
import project.lms_rikkei_edu.modules.quiz.entity.QuizQuestionEntity;
import project.lms_rikkei_edu.modules.quiz.enums.*;
import project.lms_rikkei_edu.modules.quiz.repository.*;
import project.lms_rikkei_edu.modules.quiz.service.impl.QuizStatsServiceImpl;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizStatsServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private QuizAttemptAnswerRepository answerRepository;
    @Mock private QuizQuestionRepository questionRepository;

    @InjectMocks
    private QuizStatsServiceImpl statsService;

    private UUID courseId, quizId, studentId;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        studentId = UUID.randomUUID();
    }

    // ── getQuizStats ──────────────────────────────────────────────────────────

    @Test
    void getQuizStats_withAttempts_returnsAggregatedStats() {
        QuizEntity quiz = buildQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.countByQuizIdAndStatus(quizId, AttemptStatus.GRADED)).thenReturn(10L);
        when(attemptRepository.countDistinctStudentsByQuizId(quizId)).thenReturn(8L);
        when(attemptRepository.countPassedByQuizId(quizId)).thenReturn(6L);
        when(attemptRepository.avgScoreByQuizId(quizId)).thenReturn(7.5);
        when(attemptRepository.avgScorePercentageByQuizId(quizId)).thenReturn(75.0);
        when(attemptRepository.avgTimeSpentByQuizId(quizId)).thenReturn(1200.0);
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        QuizStatsResponse result = statsService.getQuizStats(courseId, quizId);

        assertThat(result.getTotalAttempts()).isEqualTo(10);
        assertThat(result.getUniqueStudents()).isEqualTo(8);
        assertThat(result.getPassCount()).isEqualTo(6);
        assertThat(result.getPassRate()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(result.getAvgScorePercentage()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void getQuizStats_noAttempts_returnsZeroPassRate() {
        QuizEntity quiz = buildQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.countByQuizIdAndStatus(quizId, AttemptStatus.GRADED)).thenReturn(0L);
        when(attemptRepository.countDistinctStudentsByQuizId(quizId)).thenReturn(0L);
        when(attemptRepository.countPassedByQuizId(quizId)).thenReturn(0L);
        when(attemptRepository.avgScoreByQuizId(quizId)).thenReturn(null);
        when(attemptRepository.avgScorePercentageByQuizId(quizId)).thenReturn(null);
        when(attemptRepository.avgTimeSpentByQuizId(quizId)).thenReturn(null);
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        QuizStatsResponse result = statsService.getQuizStats(courseId, quizId);

        assertThat(result.getPassRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAvgScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getQuizStats_quizNotFound_throwsException() {
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statsService.getQuizStats(courseId, quizId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getQuizStats_withQuestions_includesPerQuestionStats() {
        QuizEntity quiz = buildQuiz();
        QuizQuestionEntity q = buildQuestion();
        UUID qId = q.getId();

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.countByQuizIdAndStatus(quizId, AttemptStatus.GRADED)).thenReturn(5L);
        when(attemptRepository.countDistinctStudentsByQuizId(quizId)).thenReturn(5L);
        when(attemptRepository.countPassedByQuizId(quizId)).thenReturn(3L);
        when(attemptRepository.avgScoreByQuizId(quizId)).thenReturn(8.0);
        when(attemptRepository.avgScorePercentageByQuizId(quizId)).thenReturn(80.0);
        when(attemptRepository.avgTimeSpentByQuizId(quizId)).thenReturn(900.0);
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(q));
        // 5 tổng, 4 đúng → 80%
        Object[] row = new Object[]{qId, 5L, 4L};
        List<Object[]> answerStats = new java.util.ArrayList<>();
        answerStats.add(row);
        when(answerRepository.countCorrectByQuestionIds(anyList()))
                .thenReturn(answerStats);

        QuizStatsResponse result = statsService.getQuizStats(courseId, quizId);

        assertThat(result.getQuestionStats()).hasSize(1);
        QuizQuestionStatsResponse qStats = result.getQuestionStats().get(0);
        assertThat(qStats.getTotalAnswers()).isEqualTo(5);
        assertThat(qStats.getCorrectCount()).isEqualTo(4);
        assertThat(qStats.getCorrectRate()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    // ── getStudentAttemptHistory ───────────────────────────────────────────────

    @Test
    void getStudentAttemptHistory_returnsOrderedAttempts() {
        QuizEntity quiz = buildQuiz();
        QuizAttemptEntity a1 = buildGradedAttempt(1, 70.0);
        QuizAttemptEntity a2 = buildGradedAttempt(2, 85.0);

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNumber(quizId, studentId))
                .thenReturn(List.of(a1, a2));

        List<AttemptHistoryEntry> result = statsService.getStudentAttemptHistory(courseId, quizId, studentId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAttemptNumber()).isEqualTo(1);
        assertThat(result.get(1).getAttemptNumber()).isEqualTo(2);
    }

    @Test
    void getStudentAttemptHistory_noAttempts_returnsEmptyList() {
        QuizEntity quiz = buildQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNumber(quizId, studentId))
                .thenReturn(List.of());

        List<AttemptHistoryEntry> result = statsService.getStudentAttemptHistory(courseId, quizId, studentId);

        assertThat(result).isEmpty();
    }

    // ── getStudentCourseProgress ───────────────────────────────────────────────

    @Test
    void getStudentCourseProgress_noAttempts_returnsCanRetryTrue() {
        QuizEntity quiz = buildQuiz();
        when(quizRepository.findByCourseId(courseId)).thenReturn(List.of(quiz));
        when(attemptRepository.countByQuizIdAndStudentId(quizId, studentId)).thenReturn(0L);
        when(attemptRepository.findBestAttemptByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.empty());

        List<StudentQuizProgressEntry> result = statsService.getStudentCourseProgress(courseId, studentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAttemptsUsed()).isEqualTo(0);
        assertThat(result.get(0).isCanRetry()).isTrue();
    }

    @Test
    void getStudentCourseProgress_maxAttemptsReached_canRetryFalse() {
        QuizEntity quiz = buildQuiz();
        quiz.setMaxAttempts(3);
        QuizAttemptEntity latest = buildGradedAttempt(3, 60.0);
        latest.setSubmittedAt(OffsetDateTime.now().minusHours(2));

        when(quizRepository.findByCourseId(courseId)).thenReturn(List.of(quiz));
        when(attemptRepository.countByQuizIdAndStudentId(quizId, studentId)).thenReturn(3L);
        when(attemptRepository.findBestAttemptByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.of(latest));

        List<StudentQuizProgressEntry> result = statsService.getStudentCourseProgress(courseId, studentId);

        assertThat(result.get(0).isCanRetry()).isFalse();
        assertThat(result.get(0).getAttemptsUsed()).isEqualTo(3);
    }

    @Test
    void getStudentCourseProgress_cooldownNotPassed_canRetryFalse() {
        QuizEntity quiz = buildQuiz();
        quiz.setMaxAttempts(5);
        quiz.setCooldownMinutes(20);

        QuizAttemptEntity latest = buildGradedAttempt(1, 70.0);
        latest.setSubmittedAt(OffsetDateTime.now().minusMinutes(5)); // mới nộp 5 phút trước

        when(quizRepository.findByCourseId(courseId)).thenReturn(List.of(quiz));
        when(attemptRepository.countByQuizIdAndStudentId(quizId, studentId)).thenReturn(1L);
        when(attemptRepository.findBestAttemptByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.of(latest));
        when(attemptRepository.findLatestByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.of(latest));

        List<StudentQuizProgressEntry> result = statsService.getStudentCourseProgress(courseId, studentId);

        assertThat(result.get(0).isCanRetry()).isFalse();
    }

    @Test
    void getStudentCourseProgress_cooldownPassed_canRetryTrue() {
        QuizEntity quiz = buildQuiz();
        quiz.setMaxAttempts(5);
        quiz.setCooldownMinutes(20);

        QuizAttemptEntity latest = buildGradedAttempt(1, 70.0);
        latest.setSubmittedAt(OffsetDateTime.now().minusMinutes(30)); // đã qua 30 phút

        when(quizRepository.findByCourseId(courseId)).thenReturn(List.of(quiz));
        when(attemptRepository.countByQuizIdAndStudentId(quizId, studentId)).thenReturn(1L);
        when(attemptRepository.findBestAttemptByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.of(latest));
        when(attemptRepository.findLatestByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.of(latest));

        List<StudentQuizProgressEntry> result = statsService.getStudentCourseProgress(courseId, studentId);

        assertThat(result.get(0).isCanRetry()).isTrue();
    }

    // ── getAllAttemptsForQuiz ──────────────────────────────────────────────────

    @Test
    void getAllAttemptsForQuiz_returnsAllAttempts() {
        QuizEntity quiz = buildQuiz();
        QuizAttemptEntity a1 = buildGradedAttempt(1, 80.0);
        QuizAttemptEntity a2 = buildGradedAttempt(2, 60.0);

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizId(quizId)).thenReturn(List.of(a1, a2));

        List<AttemptHistoryEntry> result = statsService.getAllAttemptsForQuiz(courseId, quizId);

        assertThat(result).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QuizEntity buildQuiz() {
        QuizEntity q = new QuizEntity();
        q.setId(quizId);
        q.setCourseId(courseId);
        q.setTitle("Test Quiz");
        q.setQuizType(QuizType.STATIC);
        q.setStatus(QuizStatus.PUBLISHED);
        q.setMaxAttempts(3);
        q.setCooldownMinutes(20);
        q.setPassScore(new BigDecimal("60.00"));
        return q;
    }

    private QuizAttemptEntity buildGradedAttempt(int number, double pct) {
        QuizAttemptEntity a = new QuizAttemptEntity();
        a.setId(UUID.randomUUID());
        a.setQuizId(quizId);
        a.setStudentId(studentId);
        a.setAttemptNumber(number);
        a.setStatus(AttemptStatus.GRADED);
        a.setScore(BigDecimal.valueOf(pct / 10));
        a.setScorePercentage(BigDecimal.valueOf(pct));
        a.setIsPassed(pct >= 60.0);
        a.setCorrectCount((int) (pct / 10));
        a.setIncorrectCount(0);
        a.setUnansweredCount(0);
        a.setViolationCount(0);
        a.setAutoSubmitted(false);
        a.setStartedAt(OffsetDateTime.now().minusMinutes(30));
        return a;
    }

    private QuizQuestionEntity buildQuestion() {
        QuizQuestionEntity q = new QuizQuestionEntity();
        q.setId(UUID.randomUUID());
        q.setQuizId(quizId);
        q.setQuestionText("Sample question?");
        q.setQuestionType(QuestionType.SINGLE_CHOICE);
        q.setDifficulty(QuestionDifficulty.MEDIUM);
        q.setPoints(BigDecimal.ONE);
        q.setOrderIndex(0);
        return q;
    }
}
