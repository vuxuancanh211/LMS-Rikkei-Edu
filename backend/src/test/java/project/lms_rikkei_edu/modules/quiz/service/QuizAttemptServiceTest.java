package project.lms_rikkei_edu.modules.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.quiz.dto.request.AutosaveRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.SubmitAttemptRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AttemptResultResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.StartAttemptResponse;
import project.lms_rikkei_edu.modules.quiz.entity.*;
import project.lms_rikkei_edu.modules.quiz.enums.*;
import project.lms_rikkei_edu.modules.quiz.repository.*;
import project.lms_rikkei_edu.modules.quiz.service.impl.QuizAttemptServiceImpl;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizAttemptServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private QuizAttemptAnswerRepository answerRepository;
    @Mock private QuizQuestionRepository questionRepository;
    @Mock private QuizOptionRepository optionRepository;
    @Mock private BankQuestionRepository bankQuestionRepository;
    @Mock private BankOptionRepository bankOptionRepository;
    @Mock private RedisService redisService;

    private QuizAttemptServiceImpl service;

    private UUID courseId, quizId, studentId, attemptId;

    @BeforeEach
    void setUp() {
        service = new QuizAttemptServiceImpl(
                quizRepository, attemptRepository, answerRepository,
                questionRepository, optionRepository, bankQuestionRepository,
                bankOptionRepository, redisService, new ObjectMapper()
        );
        courseId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
    }

    // ── Start ─────────────────────────────────────────────────────────────────

    @Test
    void startAttempt_validFirstAttempt_returnsResponse() {
        QuizEntity quiz = buildPublishedQuiz();
        QuizQuestionEntity q = buildQuestion();

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.countByQuizIdAndStudentId(quizId, studentId)).thenReturn(0L);
        when(attemptRepository.findLatestByQuizIdAndStudentId(quizId, studentId)).thenReturn(Optional.empty());
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(q));
        when(optionRepository.findByQuestionIdOrderByOrderIndex(q.getId())).thenReturn(List.of());
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StartAttemptResponse result = service.startAttempt(courseId, quizId, studentId, "127.0.0.1");

        assertThat(result.getQuizId()).isEqualTo(quizId);
        assertThat(result.getAttemptNumber()).isEqualTo(1);
        assertThat(result.getQuestions()).hasSize(1);
    }

    @Test
    void startAttempt_quizNotPublished_throwsException() {
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startAttempt(courseId, quizId, studentId, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được xuất bản");
    }

    @Test
    void startAttempt_alreadyInProgress_throwsException() {
        QuizEntity quiz = buildPublishedQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(new QuizAttemptEntity()));

        assertThatThrownBy(() -> service.startAttempt(courseId, quizId, studentId, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa hoàn thành");
    }

    @Test
    void startAttempt_maxAttemptsReached_throwsException() {
        QuizEntity quiz = buildPublishedQuiz();
        quiz.setMaxAttempts(2);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.countByQuizIdAndStudentId(quizId, studentId)).thenReturn(2L);

        assertThatThrownBy(() -> service.startAttempt(courseId, quizId, studentId, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hết số lần làm bài");
    }

    @Test
    void startAttempt_cooldownNotPassed_throwsException() {
        QuizEntity quiz = buildPublishedQuiz();
        quiz.setCooldownMinutes(20);

        QuizAttemptEntity lastAttempt = new QuizAttemptEntity();
        lastAttempt.setSubmittedAt(OffsetDateTime.now().minusMinutes(10)); // chỉ 10 phút trước

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(quizId, studentId, AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.countByQuizIdAndStudentId(quizId, studentId)).thenReturn(1L);
        when(attemptRepository.findLatestByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.of(lastAttempt));

        assertThatThrownBy(() -> service.startAttempt(courseId, quizId, studentId, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chờ");
    }

    // ── Autosave ──────────────────────────────────────────────────────────────

    @Test
    void autosave_inProgressAttempt_savesToRedis() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        AutosaveRequest req = new AutosaveRequest();
        req.setAnswers(Map.of(UUID.randomUUID(), List.of(UUID.randomUUID())));

        service.autosave(attemptId, studentId, req);

        verify(redisService).set(eq("quiz:autosave:" + attemptId), anyString(), eq(86400L));
    }

    @Test
    void autosave_attemptBelongsToOtherStudent_throwsException() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        attempt.setStudentId(UUID.randomUUID()); // khác student
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.autosave(attemptId, studentId, new AutosaveRequest()))
                .isInstanceOf(BusinessException.class);
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Test
    void submit_singleChoiceCorrectAnswer_scoredCorrectly() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        QuizQuestionEntity q = buildQuestion();
        UUID correctOptId = UUID.randomUUID();
        QuizOptionEntity correctOpt = buildOption(correctOptId, q.getId(), true);
        QuizOptionEntity wrongOpt = buildOption(UUID.randomUUID(), q.getId(), false);

        QuizEntity quiz = buildPublishedQuiz();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(q));
        when(optionRepository.findByQuestionIdOrderByOrderIndex(q.getId()))
                .thenReturn(List.of(correctOpt, wrongOpt));
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        when(answerRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmitAttemptRequest req = new SubmitAttemptRequest();
        req.setAnswers(Map.of(q.getId(), List.of(correctOptId)));

        AttemptResultResponse result = service.submit(attemptId, studentId, req);

        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.getIncorrectCount()).isEqualTo(0);
        assertThat(result.getScore()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.getStatus()).isEqualTo(AttemptStatus.GRADED);
    }

    @Test
    void submit_wrongAnswer_zeroPoints() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        QuizQuestionEntity q = buildQuestion();
        UUID correctOptId = UUID.randomUUID();
        UUID wrongOptId = UUID.randomUUID();
        QuizOptionEntity correctOpt = buildOption(correctOptId, q.getId(), true);
        QuizOptionEntity wrongOpt = buildOption(wrongOptId, q.getId(), false);

        QuizEntity quiz = buildPublishedQuiz();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(q));
        when(optionRepository.findByQuestionIdOrderByOrderIndex(q.getId()))
                .thenReturn(List.of(correctOpt, wrongOpt));
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        when(answerRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubmitAttemptRequest req = new SubmitAttemptRequest();
        req.setAnswers(Map.of(q.getId(), List.of(wrongOptId)));

        AttemptResultResponse result = service.submit(attemptId, studentId, req);

        assertThat(result.getIncorrectCount()).isEqualTo(1);
        assertThat(result.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void submit_noAnswerProvided_fallsBackToRedis() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        QuizQuestionEntity q = buildQuestion();
        UUID correctOptId = UUID.randomUUID();
        QuizOptionEntity correctOpt = buildOption(correctOptId, q.getId(), true);
        QuizEntity quiz = buildPublishedQuiz();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(q));
        when(optionRepository.findByQuestionIdOrderByOrderIndex(q.getId())).thenReturn(List.of(correctOpt));
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        when(answerRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Redis trả về empty → answers rỗng
        when(redisService.get("quiz:autosave:" + attemptId)).thenReturn(Optional.empty());

        SubmitAttemptRequest req = new SubmitAttemptRequest(); // answers = null

        AttemptResultResponse result = service.submit(attemptId, studentId, req);

        assertThat(result.getUnansweredCount()).isEqualTo(1);
        verify(redisService).get("quiz:autosave:" + attemptId);
    }

    @Test
    void submit_alreadySubmitted_throwsException() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        attempt.setStatus(AttemptStatus.GRADED);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.submit(attemptId, studentId, new SubmitAttemptRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được nộp");
    }

    @Test
    void submit_deletesAutosaveFromRedis() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        QuizQuestionEntity q = buildQuestion();
        QuizOptionEntity opt = buildOption(UUID.randomUUID(), q.getId(), true);
        QuizEntity quiz = buildPublishedQuiz();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(q));
        when(optionRepository.findByQuestionIdOrderByOrderIndex(q.getId())).thenReturn(List.of(opt));
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        when(answerRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submit(attemptId, studentId, new SubmitAttemptRequest());

        verify(redisService).delete("quiz:autosave:" + attemptId);
    }

    // ── GetResult ─────────────────────────────────────────────────────────────

    @Test
    void getResult_gradedAttempt_returnsResult() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        attempt.setStatus(AttemptStatus.GRADED);
        attempt.setScore(BigDecimal.ONE);
        attempt.setCorrectCount(1);
        attempt.setIncorrectCount(0);
        attempt.setUnansweredCount(0);

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(answerRepository.findByAttemptId(attemptId)).thenReturn(List.of());
        when(questionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        AttemptResultResponse result = service.getResult(attemptId, studentId);

        assertThat(result.getAttemptId()).isEqualTo(attemptId);
        assertThat(result.getStatus()).isEqualTo(AttemptStatus.GRADED);
    }

    @Test
    void getResult_differentUser_throwsException() {
        QuizAttemptEntity attempt = buildInProgressAttempt();
        attempt.setStatus(AttemptStatus.GRADED);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.getResult(attemptId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QuizEntity buildPublishedQuiz() {
        QuizEntity q = new QuizEntity();
        q.setId(quizId);
        q.setCourseId(courseId);
        q.setStatus(QuizStatus.PUBLISHED);
        q.setQuizType(QuizType.STATIC);
        q.setDurationMinutes(30);
        q.setMaxAttempts(3);
        q.setCooldownMinutes(20);
        return q;
    }

    private QuizAttemptEntity buildInProgressAttempt() {
        QuizAttemptEntity a = new QuizAttemptEntity();
        a.setId(attemptId);
        a.setQuizId(quizId);
        a.setCourseId(courseId);
        a.setStudentId(studentId);
        a.setStatus(AttemptStatus.IN_PROGRESS);
        a.setAttemptNumber(1);
        a.setStartedAt(OffsetDateTime.now().minusMinutes(5));
        return a;
    }

    private QuizQuestionEntity buildQuestion() {
        QuizQuestionEntity q = new QuizQuestionEntity();
        q.setId(UUID.randomUUID());
        q.setQuizId(quizId);
        q.setQuestionText("What is 1+1?");
        q.setQuestionType(QuestionType.SINGLE_CHOICE);
        q.setDifficulty(QuestionDifficulty.EASY);
        q.setPoints(BigDecimal.ONE);
        q.setOrderIndex(0);
        return q;
    }

    private QuizOptionEntity buildOption(UUID id, UUID questionId, boolean correct) {
        QuizOptionEntity o = new QuizOptionEntity();
        o.setId(id);
        o.setQuestionId(questionId);
        o.setOptionText(correct ? "2" : "3");
        o.setIsCorrect(correct);
        o.setOrderIndex(0);
        return o;
    }
}
