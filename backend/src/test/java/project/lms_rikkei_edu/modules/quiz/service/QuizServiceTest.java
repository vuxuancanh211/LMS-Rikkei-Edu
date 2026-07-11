package project.lms_rikkei_edu.modules.quiz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.course.repository.LessonRepository;
import project.lms_rikkei_edu.modules.quiz.dto.request.*;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.entity.*;
import project.lms_rikkei_edu.modules.quiz.enums.*;
import project.lms_rikkei_edu.modules.quiz.repository.*;
import project.lms_rikkei_edu.modules.quiz.service.impl.QuizServiceImpl;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizOptionRepository quizOptionRepository;
    @Mock private BankQuestionRepository bankQuestionRepository;
    @Mock private BankOptionRepository bankOptionRepository;
    @Mock private LessonRepository lessonRepository;

    @InjectMocks
    private QuizServiceImpl quizService;

    private UUID courseId;
    private UUID quizId;
    private UUID instructorId;

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savedWithDraftStatus() {
        QuizMetadataRequest req = buildMetadataRequest(QuizType.STATIC);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quizQuestionRepository.countByQuizId(any())).thenReturn(0L);

        QuizSummaryResponse result = quizService.create(courseId, instructorId, req);

        assertThat(result.getTitle()).isEqualTo("Test Quiz");
        assertThat(result.getStatus()).isEqualTo(QuizStatus.DRAFT);
        verify(quizRepository).save(any(QuizEntity.class));
    }

    // ── UpdateMetadata ────────────────────────────────────────────────────────

    @Test
    void updateMetadata_draftQuiz_updatesFields() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quizQuestionRepository.countByQuizId(any())).thenReturn(0L);

        QuizMetadataRequest req = buildMetadataRequest(QuizType.STATIC);
        req.setTitle("Updated Title");
        QuizSummaryResponse result = quizService.updateMetadata(courseId, quizId, req);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void updateMetadata_publishedQuiz_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setStatus(QuizStatus.PUBLISHED);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.updateMetadata(courseId, quizId, buildMetadataRequest(QuizType.STATIC)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DRAFT");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_draftQuiz_deletedSuccessfully() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(lessonRepository.findByQuizId(quizId)).thenReturn(Optional.empty());
        doNothing().when(quizQuestionRepository).deleteByQuizId(quizId);

        quizService.delete(courseId, quizId);

        verify(quizRepository).delete(quiz);
    }

    @Test
    void delete_publishedQuiz_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setStatus(QuizStatus.PUBLISHED);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.delete(courseId, quizId))
                .isInstanceOf(BusinessException.class);
    }

    // ── AddBankQuestions ──────────────────────────────────────────────────────

    @Test
    void addBankQuestions_validIds_snapshotsCreated() {
        QuizEntity quiz = buildDraftQuiz();
        UUID bankQId = UUID.randomUUID();
        BankQuestionEntity bankQ = buildBankQuestion(bankQId);

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(0L);
        when(bankQuestionRepository.findById(bankQId)).thenReturn(Optional.of(bankQ));
        when(quizQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(bankQId)).thenReturn(List.of());
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        QuizAddBankQuestionsRequest req = new QuizAddBankQuestionsRequest();
        req.setBankQuestionIds(List.of(bankQId));

        QuizDetailResponse result = quizService.addBankQuestions(courseId, quizId, req);

        verify(quizQuestionRepository).save(any(QuizQuestionEntity.class));
    }

    @Test
    void addBankQuestions_questionFromOtherCourse_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        UUID bankQId = UUID.randomUUID();
        BankQuestionEntity bankQ = buildBankQuestion(bankQId);
        bankQ.setCourseId(UUID.randomUUID()); // other course

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(0L);
        when(bankQuestionRepository.findById(bankQId)).thenReturn(Optional.of(bankQ));

        QuizAddBankQuestionsRequest req = new QuizAddBankQuestionsRequest();
        req.setBankQuestionIds(List.of(bankQId));

        assertThatThrownBy(() -> quizService.addBankQuestions(courseId, quizId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc khóa học");
    }

    // ── Publish ───────────────────────────────────────────────────────────────

    @Test
    void publish_draftWithQuestions_statusChangedToPublished() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(3L);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizSummaryResponse result = quizService.publish(courseId, quizId);

        assertThat(result.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
    }

    @Test
    void publish_draftWithNoQuestions_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(0L);

        assertThatThrownBy(() -> quizService.publish(courseId, quizId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 1 câu hỏi");
    }

    @Test
    void publish_alreadyPublished_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setStatus(QuizStatus.PUBLISHED);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.publish(courseId, quizId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xuất bản");
    }

    @Test
    void publish_randomDrawWithoutMode_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        // randomMode not set
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.publish(courseId, quizId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("random mode");
    }

    // ── Archive / Unarchive ───────────────────────────────────────────────────

    @Test
    void archive_publishedQuiz_statusChangedToArchived() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setStatus(QuizStatus.PUBLISHED);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizSummaryResponse result = quizService.archive(courseId, quizId);

        assertThat(result.getStatus()).isEqualTo(QuizStatus.ARCHIVED);
    }

    @Test
    void archive_draftQuiz_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.archive(courseId, quizId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PUBLISHED");
    }

    @Test
    void unarchive_archivedQuiz_statusChangedToPublished() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setStatus(QuizStatus.ARCHIVED);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizSummaryResponse result = quizService.unarchive(courseId, quizId);

        assertThat(result.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
    }

    // ── Dry Run ───────────────────────────────────────────────────────────────

    @Test
    void dryRun_draftStaticQuiz_returnsQuestions() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        DryRunResponse result = quizService.dryRun(courseId, quizId);

        assertThat(result.getNote()).contains("xem thử");
    }

    @Test
    void dryRun_publishedQuiz_returnsQuestions() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setStatus(QuizStatus.PUBLISHED);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        DryRunResponse result = quizService.dryRun(courseId, quizId);

        assertThat(result.getNote()).contains("xem thử");
    }

    // ── AutoArchive ───────────────────────────────────────────────────────────

    @Test
    void autoArchiveExpiredQuizzes_archivesExpiredOnes() {
        QuizEntity expiredQuiz = buildDraftQuiz();
        expiredQuiz.setStatus(QuizStatus.PUBLISHED);
        expiredQuiz.setEndDate(OffsetDateTime.now().minusHours(1));

        when(quizRepository.findByStatusAndEndDateBefore(eq(QuizStatus.PUBLISHED), any()))
                .thenReturn(List.of(expiredQuiz));
        when(quizRepository.saveAll(any())).thenReturn(List.of(expiredQuiz));

        quizService.autoArchiveExpiredQuizzes();

        assertThat(expiredQuiz.getStatus()).isEqualTo(QuizStatus.ARCHIVED);
        assertThat(expiredQuiz.getArchivedAt()).isNotNull();
        verify(quizRepository).saveAll(anyList());
    }

    // ── Random Draw Config ────────────────────────────────────────────────────

    @Test
    void configureRandomDraw_fullyRandom_notEnoughInBank_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(bankQuestionRepository.countByCourseIdAndStatus(courseId, QuestionStatus.ACTIVE)).thenReturn(5L);

        QuizRandomConfigRequest req = new QuizRandomConfigRequest();
        req.setRandomMode(RandomMode.FULLY_RANDOM);
        req.setTotalCount(10);

        assertThatThrownBy(() -> quizService.configureRandomDraw(courseId, quizId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không đủ");
    }

    @Test
    void configureRandomDraw_notRandomDrawType_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.STATIC);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.configureRandomDraw(courseId, quizId, new QuizRandomConfigRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Random Draw");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QuizEntity buildDraftQuiz() {
        QuizEntity quiz = new QuizEntity();
        quiz.setId(quizId);
        quiz.setCourseId(courseId);
        quiz.setCreatedBy(instructorId);
        quiz.setTitle("Test Quiz");
        quiz.setQuizType(QuizType.STATIC);
        quiz.setStatus(QuizStatus.DRAFT);
        quiz.setMaxAttempts(3);
        quiz.setCooldownMinutes(20);
        return quiz;
    }

    private QuizMetadataRequest buildMetadataRequest(QuizType type) {
        QuizMetadataRequest req = new QuizMetadataRequest();
        req.setTitle("Test Quiz");
        req.setQuizType(type);
        req.setDurationMinutes(30);
        return req;
    }

    private BankQuestionEntity buildBankQuestion(UUID id) {
        BankQuestionEntity q = new BankQuestionEntity();
        q.setId(id);
        q.setCourseId(courseId);
        q.setQuestionText("Sample question?");
        q.setQuestionType(QuestionType.SINGLE_CHOICE);
        q.setDifficulty(QuestionDifficulty.MEDIUM);
        q.setStatus(QuestionStatus.ACTIVE);
        return q;
    }
}
