package project.lms_rikkei_edu.modules.quiz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.CourseEnrollmentEntity;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.repository.CourseEnrollmentRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonRepository;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;
import project.lms_rikkei_edu.modules.quiz.dto.request.*;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.entity.*;
import project.lms_rikkei_edu.modules.quiz.enums.*;
import project.lms_rikkei_edu.modules.quiz.repository.*;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService;
import project.lms_rikkei_edu.modules.quiz.service.impl.QuizServiceImpl;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizOptionRepository quizOptionRepository;
    @Mock private BankQuestionRepository bankQuestionRepository;
    @Mock private BankOptionRepository bankOptionRepository;
    @Mock private BankQuestionEmbeddingService bankQuestionEmbeddingService;
    @Mock private LessonRepository lessonRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationPreferenceService notificationPreferenceService;
    @Mock private CurrentUserProvider currentUserProvider;

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

    // ── Delete: lesson-attached guard ────────────────────────────────────────

    @Test
    void delete_quizAttachedToLesson_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(lessonRepository.findByQuizId(quizId)).thenReturn(Optional.of(new Lesson()));

        assertThatThrownBy(() -> quizService.delete(courseId, quizId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gỡ khỏi bài học");
        verify(quizRepository, never()).delete(any());
    }

    // ── GetDetail ─────────────────────────────────────────────────────────────

    @Test
    void getDetail_found_returnsDetailWithQuestions() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        QuizDetailResponse result = quizService.getDetail(courseId, quizId);

        assertThat(result.getId()).isEqualTo(quizId);
        assertThat(result.getQuestions()).isEmpty();
    }

    @Test
    void getDetail_notFound_throwsException() {
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getDetail(courseId, quizId))
                .isInstanceOf(BusinessException.class);
    }

    // ── ListByCourse ──────────────────────────────────────────────────────────

    @Test
    void listByCourse_titleProvided_usesTitleSearch() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(quizRepository.findByCourseIdAndTitleContainingIgnoreCase(courseId, "math", pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        quizService.listByCourse(courseId, "  math  ", pageable);

        verify(quizRepository).findByCourseIdAndTitleContainingIgnoreCase(courseId, "math", pageable);
        verify(quizRepository, never()).findByCourseId(any(), any());
    }

    @Test
    void listByCourse_blankTitle_usesFindByCourseId() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(quizRepository.findByCourseId(courseId, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        quizService.listByCourse(courseId, "   ", pageable);

        verify(quizRepository).findByCourseId(courseId, pageable);
    }

    // ── AddBankQuestions: multi-id ────────────────────────────────────────────

    @Test
    void addBankQuestions_multipleIds_incrementsOrderAcrossLoop() {
        QuizEntity quiz = buildDraftQuiz();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(2L);
        when(bankQuestionRepository.findById(id1)).thenReturn(Optional.of(buildBankQuestion(id1)));
        when(bankQuestionRepository.findById(id2)).thenReturn(Optional.of(buildBankQuestion(id2)));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(any())).thenReturn(List.of());
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        org.mockito.ArgumentCaptor<QuizQuestionEntity> captor = org.mockito.ArgumentCaptor.forClass(QuizQuestionEntity.class);
        when(quizQuestionRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        QuizAddBankQuestionsRequest req = new QuizAddBankQuestionsRequest();
        req.setBankQuestionIds(List.of(id1, id2));

        quizService.addBankQuestions(courseId, quizId, req);

        List<QuizQuestionEntity> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getOrderIndex()).isEqualTo(2);
        assertThat(saved.get(1).getOrderIndex()).isEqualTo(3);
    }

    @Test
    void addBankQuestions_idNotFound_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        UUID missingId = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(0L);
        when(bankQuestionRepository.findById(missingId)).thenReturn(Optional.empty());

        QuizAddBankQuestionsRequest req = new QuizAddBankQuestionsRequest();
        req.setBankQuestionIds(List.of(missingId));

        assertThatThrownBy(() -> quizService.addBankQuestions(courseId, quizId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc khóa học");
    }

    // ── AddManualQuestion ─────────────────────────────────────────────────────

    @Test
    void addManualQuestion_validSingleChoice_savedWithoutBank() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(0L);
        when(quizQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        QuizManualQuestionRequest req = buildManualRequest(QuestionType.SINGLE_CHOICE, false,
                option("A", true), option("B", false));

        quizService.addManualQuestion(courseId, quizId, instructorId, req);

        verify(quizQuestionRepository).save(argThat(qq -> qq.getBankQuestionId() == null));
        verify(bankQuestionRepository, never()).saveAndFlush(any());
        verify(quizOptionRepository, times(2)).save(any());
    }

    @Test
    void addManualQuestion_saveToBankTrue_persistsToBankAndLinksId() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(0L);
        when(bankQuestionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            BankQuestionEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(quizQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        QuizManualQuestionRequest req = buildManualRequest(QuestionType.SINGLE_CHOICE, true,
                option("A", true), option("B", false));

        quizService.addManualQuestion(courseId, quizId, instructorId, req);

        verify(bankQuestionRepository).saveAndFlush(any());
        verify(bankQuestionEmbeddingService).embedAndSaveSafe(any(), eq(req.getQuestionText()));
        verify(quizQuestionRepository).save(argThat(qq -> qq.getBankQuestionId() != null));
    }

    @Test
    void addManualQuestion_lessThanTwoOptions_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        QuizManualQuestionRequest req = buildManualRequest(QuestionType.SINGLE_CHOICE, false, option("A", true));

        assertThatThrownBy(() -> quizService.addManualQuestion(courseId, quizId, instructorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 2 đáp án");
    }

    @Test
    void addManualQuestion_singleChoiceWrongCorrectCount_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        QuizManualQuestionRequest req = buildManualRequest(QuestionType.SINGLE_CHOICE, false,
                option("A", true), option("B", true));

        assertThatThrownBy(() -> quizService.addManualQuestion(courseId, quizId, instructorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đúng 1 đáp án đúng");
    }

    @Test
    void addManualQuestion_multipleChoiceNotEnoughCorrect_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        QuizManualQuestionRequest req = buildManualRequest(QuestionType.MULTIPLE_CHOICE, false,
                option("A", true), option("B", false));

        assertThatThrownBy(() -> quizService.addManualQuestion(courseId, quizId, instructorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 2 đáp án đúng");
    }

    // ── RemoveQuestion ────────────────────────────────────────────────────────

    @Test
    void removeQuestion_existingQuestion_deletesAndRenormalizes() {
        QuizEntity quiz = buildDraftQuiz();
        UUID questionId = UUID.randomUUID();
        QuizQuestionEntity qq = new QuizQuestionEntity();
        qq.setId(questionId);
        qq.setQuizId(quizId);

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(qq));

        QuizQuestionEntity remaining1 = new QuizQuestionEntity();
        remaining1.setId(UUID.randomUUID());
        remaining1.setOrderIndex(5);
        QuizQuestionEntity remaining2 = new QuizQuestionEntity();
        remaining2.setId(UUID.randomUUID());
        remaining2.setOrderIndex(7);
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(remaining1, remaining2));

        quizService.removeQuestion(courseId, quizId, questionId);

        verify(quizOptionRepository).deleteByQuestionId(questionId);
        verify(quizQuestionRepository).delete(qq);
        assertThat(remaining1.getOrderIndex()).isEqualTo(0);
        assertThat(remaining2.getOrderIndex()).isEqualTo(1);
        verify(quizQuestionRepository).saveAll(List.of(remaining1, remaining2));
    }

    @Test
    void removeQuestion_notFound_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        UUID questionId = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.removeQuestion(courseId, quizId, questionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tồn tại trong quiz này");
    }

    @Test
    void removeQuestion_belongsToDifferentQuiz_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        UUID questionId = UUID.randomUUID();
        QuizQuestionEntity qq = new QuizQuestionEntity();
        qq.setId(questionId);
        qq.setQuizId(UUID.randomUUID()); // different quiz
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(qq));

        assertThatThrownBy(() -> quizService.removeQuestion(courseId, quizId, questionId))
                .isInstanceOf(BusinessException.class);
    }

    // ── ReorderQuestions ──────────────────────────────────────────────────────

    @Test
    void reorderQuestions_validPermutation_setsOrderIndex() {
        QuizEntity quiz = buildDraftQuiz();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        QuizQuestionEntity question1 = new QuizQuestionEntity();
        question1.setId(q1);
        QuizQuestionEntity question2 = new QuizQuestionEntity();
        question2.setId(q2);

        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(question1, question2));

        quizService.reorderQuestions(courseId, quizId, List.of(q2, q1));

        assertThat(question2.getOrderIndex()).isEqualTo(0);
        assertThat(question1.getOrderIndex()).isEqualTo(1);
        verify(quizQuestionRepository).saveAll(List.of(question1, question2));
    }

    @Test
    void reorderQuestions_mismatchedIdSet_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        QuizQuestionEntity question1 = new QuizQuestionEntity();
        question1.setId(UUID.randomUUID());
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(question1));

        assertThatThrownBy(() -> quizService.reorderQuestions(courseId, quizId, List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class);
    }

    // ── ConfigureRandomDraw: BY_DIFFICULTY paths ─────────────────────────────

    @Test
    void configureRandomDraw_byDifficulty_success_setsConfig() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(bankQuestionRepository.countByCourseIdAndStatusAndDifficulty(courseId, QuestionStatus.ACTIVE, QuestionDifficulty.EASY))
                .thenReturn(10L);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizRandomConfigRequest req = new QuizRandomConfigRequest();
        req.setRandomMode(RandomMode.BY_DIFFICULTY);
        req.setDifficultyConfig(Map.of("EASY", 5));

        QuizSummaryResponse result = quizService.configureRandomDraw(courseId, quizId, req);

        assertThat(result).isNotNull();
        assertThat(quiz.getDifficultyConfig()).isEqualTo(Map.of("EASY", 5));
        assertThat(quiz.getRandomTotalCount()).isNull();
    }

    @Test
    void configureRandomDraw_byDifficultyEmptyConfig_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        QuizRandomConfigRequest req = new QuizRandomConfigRequest();
        req.setRandomMode(RandomMode.BY_DIFFICULTY);
        req.setDifficultyConfig(Map.of());

        assertThatThrownBy(() -> quizService.configureRandomDraw(courseId, quizId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("difficultyConfig");
    }

    @Test
    void configureRandomDraw_byDifficultyInvalidKey_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        QuizRandomConfigRequest req = new QuizRandomConfigRequest();
        req.setRandomMode(RandomMode.BY_DIFFICULTY);
        req.setDifficultyConfig(Map.of("INVALID", 5));

        assertThatThrownBy(() -> quizService.configureRandomDraw(courseId, quizId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Độ khó không hợp lệ");
    }

    @Test
    void configureRandomDraw_byDifficultyNotEnoughInBank_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(bankQuestionRepository.countByCourseIdAndStatusAndDifficulty(courseId, QuestionStatus.ACTIVE, QuestionDifficulty.HARD))
                .thenReturn(1L);

        QuizRandomConfigRequest req = new QuizRandomConfigRequest();
        req.setRandomMode(RandomMode.BY_DIFFICULTY);
        req.setDifficultyConfig(Map.of("HARD", 5));

        assertThatThrownBy(() -> quizService.configureRandomDraw(courseId, quizId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bank chỉ có");
    }

    @Test
    void configureRandomDraw_fullyRandom_success_setsTotalCount() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(bankQuestionRepository.countByCourseIdAndStatus(courseId, QuestionStatus.ACTIVE)).thenReturn(20L);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizRandomConfigRequest req = new QuizRandomConfigRequest();
        req.setRandomMode(RandomMode.FULLY_RANDOM);
        req.setTotalCount(10);

        quizService.configureRandomDraw(courseId, quizId, req);

        assertThat(quiz.getRandomTotalCount()).isEqualTo(10);
        assertThat(quiz.getDifficultyConfig()).isNull();
    }

    @Test
    void configureRandomDraw_fullyRandomMissingTotalCount_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        QuizRandomConfigRequest req = new QuizRandomConfigRequest();
        req.setRandomMode(RandomMode.FULLY_RANDOM);

        assertThatThrownBy(() -> quizService.configureRandomDraw(courseId, quizId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("totalCount");
    }

    // ── Publish: additional branches ─────────────────────────────────────────

    @Test
    void publish_archivedQuiz_throwsException() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setStatus(QuizStatus.ARCHIVED);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.publish(courseId, quizId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unarchive");
    }

    @Test
    void publish_randomDrawWithModeConfigured_succeedsWithoutQuestionCountCheck() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        quiz.setRandomMode(RandomMode.FULLY_RANDOM);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        QuizSummaryResponse result = quizService.publish(courseId, quizId);

        assertThat(result.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
    }

    @Test
    void publish_courseNotFound_skipsNotificationsButStillPublishes() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(1L);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        quizService.publish(courseId, quizId);

        verifyNoInteractions(courseEnrollmentRepository, notificationService);
    }

    @Test
    void publish_noEnrollments_skipsNotifications() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(1L);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Course course = new Course();
        course.setTitle("Course");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findAllByCourseId(courseId)).thenReturn(List.of());

        quizService.publish(courseId, quizId);

        verifyNoInteractions(notificationService);
    }

    @Test
    void publish_preferenceDisabled_skipsThatStudentNotification() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(1L);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Course course = new Course();
        course.setTitle("Course");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
        UUID studentId = UUID.randomUUID();
        enrollment.setStudentId(studentId);
        when(courseEnrollmentRepository.findAllByCourseId(courseId)).thenReturn(List.of(enrollment));
        when(notificationPreferenceService.isInAppEnabled(studentId, "QUIZ_PUBLISHED")).thenReturn(false);
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

        quizService.publish(courseId, quizId);

        verifyNoInteractions(notificationService);
    }

    @Test
    void publish_preferenceEnabled_createsNotification() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(1L);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Course course = new Course();
        course.setTitle("Course");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
        UUID studentId = UUID.randomUUID();
        enrollment.setStudentId(studentId);
        when(courseEnrollmentRepository.findAllByCourseId(courseId)).thenReturn(List.of(enrollment));
        when(notificationPreferenceService.isInAppEnabled(studentId, "QUIZ_PUBLISHED")).thenReturn(true);
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getUsername()).thenReturn("instructor1");
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal));

        quizService.publish(courseId, quizId);

        verify(notificationService).createNotification(
                eq(studentId), eq("QUIZ_PUBLISHED"), anyString(), anyString(),
                eq("COURSE"), eq(courseId), eq(instructorId), eq("instructor1"),
                eq("quiz-published-" + quizId + ":" + studentId));
    }

    @Test
    void publish_notificationServiceThrows_publishStillSucceeds() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.countByQuizId(quizId)).thenReturn(1L);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Course course = new Course();
        course.setTitle("Course");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseEnrollmentRepository.findAllByCourseId(courseId)).thenThrow(new RuntimeException("db down"));

        QuizSummaryResponse result = quizService.publish(courseId, quizId);

        assertThat(result.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
    }

    // ── Unarchive: guard ──────────────────────────────────────────────────────

    @Test
    void unarchive_nonArchivedQuiz_throwsException() {
        QuizEntity quiz = buildDraftQuiz(); // DRAFT
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.unarchive(courseId, quizId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ARCHIVED");
    }

    // ── DryRun: RANDOM_DRAW / shuffle branches ───────────────────────────────

    @Test
    void dryRun_randomDrawType_usesSimulateRandomDraw() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        quiz.setRandomMode(RandomMode.FULLY_RANDOM);
        quiz.setRandomTotalCount(5);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        UUID bankQId = UUID.randomUUID();
        BankQuestionEntity bq = buildBankQuestion(bankQId);
        when(bankQuestionRepository.randomFully(courseId, null, 5)).thenReturn(List.of(bq));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(bankQId)).thenReturn(List.of());

        DryRunResponse result = quizService.dryRun(courseId, quizId);

        assertThat(result.getQuestions()).hasSize(1);
        verify(quizQuestionRepository, never()).findByQuizIdOrderByOrderIndex(any());
    }

    @Test
    void dryRun_shuffleOptionsTrue_rebuildsQuestionsWithShuffledOptions() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setShuffleOptions(true);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        UUID questionId = UUID.randomUUID();
        QuizQuestionEntity qq = new QuizQuestionEntity();
        qq.setId(questionId);
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of(qq));

        QuizOptionEntity opt1 = new QuizOptionEntity();
        opt1.setId(UUID.randomUUID());
        opt1.setOptionText("A");
        QuizOptionEntity opt2 = new QuizOptionEntity();
        opt2.setId(UUID.randomUUID());
        opt2.setOptionText("B");
        when(quizOptionRepository.findByQuestionIdOrderByOrderIndex(questionId)).thenReturn(List.of(opt1, opt2));

        DryRunResponse result = quizService.dryRun(courseId, quizId);

        assertThat(result.getQuestions()).hasSize(1);
        assertThat(result.getQuestions().get(0).getOptions()).hasSize(2);
    }

    @Test
    void dryRun_staticType_noQuestions_returnsZero() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.STATIC);
        quiz.setShuffleQuestions(false);
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizIdOrderByOrderIndex(quizId)).thenReturn(List.of());

        DryRunResponse result = quizService.dryRun(courseId, quizId);

        assertThat(result.getTotalQuestions()).isZero();
    }

    // ── GradeDryRun ───────────────────────────────────────────────────────────

    @Test
    void gradeDryRun_staticQuiz_mixedAnswers_computesCorrectPercentageAndPassed() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setPassScore(BigDecimal.valueOf(50));
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        UUID correctQ = UUID.randomUUID();
        UUID wrongQ = UUID.randomUUID();
        UUID unansweredQ = UUID.randomUUID();

        UUID correctOptId = UUID.randomUUID();
        setupQuizQuestionWithOptions(correctQ, correctOptId);
        UUID wrongCorrectOptId = UUID.randomUUID();
        setupQuizQuestionWithOptions(wrongQ, wrongCorrectOptId);
        UUID unansweredCorrectOptId = UUID.randomUUID();
        setupQuizQuestionWithOptions(unansweredQ, unansweredCorrectOptId);

        DryRunGradeRequest req = new DryRunGradeRequest();
        req.setQuestionIds(List.of(correctQ, wrongQ, unansweredQ));
        Map<UUID, List<UUID>> answers = new HashMap<>();
        answers.put(correctQ, List.of(correctOptId));
        answers.put(wrongQ, List.of(UUID.randomUUID())); // wrong option
        req.setAnswers(answers);

        DryRunGradeResponse result = quizService.gradeDryRun(courseId, quizId, req);

        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.getIncorrectCount()).isEqualTo(1);
        assertThat(result.getUnansweredCount()).isEqualTo(1);
        assertThat(result.getTotalQuestions()).isEqualTo(3);
        // 1/3 = 33.33%
        assertThat(result.getScorePercentage()).isEqualByComparingTo(BigDecimal.valueOf(33.33));
        assertThat(result.getIsPassed()).isFalse();
    }

    @Test
    void gradeDryRun_randomDrawQuiz_usesBankRepositories() {
        QuizEntity quiz = buildDraftQuiz();
        quiz.setQuizType(QuizType.RANDOM_DRAW);
        quiz.setPassScore(BigDecimal.valueOf(50));
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        UUID bankQId = UUID.randomUUID();
        BankQuestionEntity bq = buildBankQuestion(bankQId);
        when(bankQuestionRepository.findById(bankQId)).thenReturn(Optional.of(bq));
        UUID correctOptId = UUID.randomUUID();
        BankOptionEntity correctOpt = new BankOptionEntity();
        correctOpt.setId(correctOptId);
        correctOpt.setIsCorrect(true);
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(bankQId)).thenReturn(List.of(correctOpt));

        DryRunGradeRequest req = new DryRunGradeRequest();
        req.setQuestionIds(List.of(bankQId));
        req.setAnswers(Map.of(bankQId, List.of(correctOptId)));

        DryRunGradeResponse result = quizService.gradeDryRun(courseId, quizId, req);

        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.getIsPassed()).isTrue();
        verify(quizQuestionRepository, never()).findById(any());
    }

    @Test
    void gradeDryRun_questionNotFound_isSkipped() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        UUID missingQ = UUID.randomUUID();
        when(quizQuestionRepository.findById(missingQ)).thenReturn(Optional.empty());

        DryRunGradeRequest req = new DryRunGradeRequest();
        req.setQuestionIds(List.of(missingQ));
        req.setAnswers(Map.of());

        DryRunGradeResponse result = quizService.gradeDryRun(courseId, quizId, req);

        assertThat(result.getTotalQuestions()).isZero();
        assertThat(result.getScorePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getIsPassed()).isFalse();
    }

    @Test
    void gradeDryRun_nullQuestionIdsAndAnswers_returnsZeroScore() {
        QuizEntity quiz = buildDraftQuiz();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        DryRunGradeRequest req = new DryRunGradeRequest();

        DryRunGradeResponse result = quizService.gradeDryRun(courseId, quizId, req);

        assertThat(result.getTotalQuestions()).isZero();
        assertThat(result.getScorePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setupQuizQuestionWithOptions(UUID questionId, UUID correctOptionId) {
        QuizQuestionEntity qq = new QuizQuestionEntity();
        qq.setId(questionId);
        when(quizQuestionRepository.findById(questionId)).thenReturn(Optional.of(qq));
        QuizOptionEntity correctOpt = new QuizOptionEntity();
        correctOpt.setId(correctOptionId);
        correctOpt.setIsCorrect(true);
        when(quizOptionRepository.findByQuestionIdOrderByOrderIndex(questionId)).thenReturn(List.of(correctOpt));
    }

    private QuizManualQuestionRequest buildManualRequest(QuestionType type, boolean saveToBank, BankOptionRequest... options) {
        QuizManualQuestionRequest req = new QuizManualQuestionRequest();
        req.setQuestionText("Manual question?");
        req.setQuestionType(type);
        req.setDifficulty(QuestionDifficulty.EASY);
        req.setOptions(List.of(options));
        req.setSaveToBank(saveToBank);
        return req;
    }

    private BankOptionRequest option(String text, boolean correct) {
        BankOptionRequest o = new BankOptionRequest();
        o.setOptionText(text);
        o.setIsCorrect(correct);
        return o;
    }

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
