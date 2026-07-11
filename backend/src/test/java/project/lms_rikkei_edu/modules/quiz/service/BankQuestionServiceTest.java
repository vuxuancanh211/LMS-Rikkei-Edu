package project.lms_rikkei_edu.modules.quiz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankOptionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionResponse;
import project.lms_rikkei_edu.modules.quiz.entity.BankOptionEntity;
import project.lms_rikkei_edu.modules.quiz.entity.BankQuestionEntity;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;
import project.lms_rikkei_edu.modules.quiz.repository.BankOptionRepository;
import project.lms_rikkei_edu.modules.quiz.repository.BankQuestionRepository;
import project.lms_rikkei_edu.modules.quiz.service.impl.BankQuestionServiceImpl;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankQuestionServiceTest {

    @Mock BankQuestionRepository bankQuestionRepository;
    @Mock BankOptionRepository bankOptionRepository;
    @Mock RedisService redisService;
    @Mock BankQuestionEmbeddingService embeddingService;
    @Mock OpenAiProperties openAiProperties;

    @InjectMocks BankQuestionServiceImpl service;

    private UUID courseId;
    private UUID instructorId;
    private UUID questionId;

    @BeforeEach
    void setUp() {
        // ObjectMapper cần inject thủ công vì @InjectMocks không inject final field
        service = new BankQuestionServiceImpl(bankQuestionRepository, bankOptionRepository,
                redisService, new ObjectMapper(), embeddingService, openAiProperties);
        courseId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        questionId = UUID.randomUUID();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void create_singleChoice_withOneCorrectAnswer_success() {
        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true), option("B", false), option("C", false)));
        when(bankQuestionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(any())).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(any())).thenReturn(false);

        BankQuestionResponse response = service.create(courseId, instructorId, request);

        assertThat(response).isNotNull();
        verify(bankQuestionRepository).saveAndFlush(any());
        verify(bankOptionRepository, times(3)).save(any());
    }

    @Test
    void create_singleChoice_withTwoCorrectAnswers_throwsException() {
        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true), option("B", true)));

        assertThatThrownBy(() -> service.create(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đúng 1 đáp án đúng");
    }

    @Test
    void create_multipleChoice_withOneCorrectAnswer_throwsException() {
        BankQuestionRequest request = buildRequest(QuestionType.MULTIPLE_CHOICE,
                List.of(option("A", true), option("B", false)));

        assertThatThrownBy(() -> service.create(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 2 đáp án đúng");
    }

    @Test
    void create_withLessThanTwoOptions_throwsException() {
        BankQuestionRequest request = buildRequest(QuestionType.SINGLE_CHOICE,
                List.of(option("A", true)));

        assertThatThrownBy(() -> service.create(courseId, instructorId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 2 đáp án");
    }

    @Test
    void create_trueFalse_withOneCorrectAnswer_success() {
        BankQuestionRequest request = buildRequest(QuestionType.TRUE_FALSE,
                List.of(option("True", true), option("False", false)));
        when(bankQuestionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(bankOptionRepository.findByBankQuestionIdOrderByOrderIndex(any())).thenReturn(List.of());
        when(bankQuestionRepository.hasQuizReference(any())).thenReturn(false);

        assertThatNoException().isThrownBy(() -> service.create(courseId, instructorId, request));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_noReference_hardDelete() {
        BankQuestionEntity q = buildEntity();
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(bankQuestionRepository.hasQuizReference(questionId)).thenReturn(false);

        service.delete(courseId, questionId);

        verify(bankOptionRepository).deleteByBankQuestionId(questionId);
        verify(bankQuestionRepository).delete(q);
        verify(bankQuestionRepository, never()).save(any());
    }

    @Test
    void delete_hasReference_softDeleteToInactive() {
        BankQuestionEntity q = buildEntity();
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));
        when(bankQuestionRepository.hasQuizReference(questionId)).thenReturn(true);

        service.delete(courseId, questionId);

        verify(bankQuestionRepository).save(q);
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.INACTIVE);
        verify(bankQuestionRepository, never()).delete(any());
    }

    @Test
    void delete_questionNotInCourse_throwsException() {
        BankQuestionEntity q = buildEntity();
        q.setCourseId(UUID.randomUUID()); // different course
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> service.delete(courseId, questionId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_questionNotFound_throwsException() {
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(courseId, questionId))
                .isInstanceOf(BusinessException.class);
    }

    // ── Toggle status ─────────────────────────────────────────────────────────

    @Test
    void toggleStatus_activeToInactive() {
        BankQuestionEntity q = buildEntity();
        q.setStatus(QuestionStatus.ACTIVE);
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));

        service.toggleStatus(courseId, questionId);

        assertThat(q.getStatus()).isEqualTo(QuestionStatus.INACTIVE);
        verify(bankQuestionRepository).save(q);
    }

    @Test
    void toggleStatus_inactiveToActive() {
        BankQuestionEntity q = buildEntity();
        q.setStatus(QuestionStatus.INACTIVE);
        when(bankQuestionRepository.findById(questionId)).thenReturn(Optional.of(q));

        service.toggleStatus(courseId, questionId);

        assertThat(q.getStatus()).isEqualTo(QuestionStatus.ACTIVE);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Test
    void list_filterByDifficulty_callsCorrectRepository() {
        when(bankQuestionRepository.findByCourseIdAndStatusAndDifficulty(
                courseId, QuestionStatus.ACTIVE, QuestionDifficulty.EASY))
                .thenReturn(List.of());

        List<BankQuestionResponse> result = service.list(courseId, null, QuestionDifficulty.EASY, null);

        assertThat(result).isEmpty();
        verify(bankQuestionRepository).findByCourseIdAndStatusAndDifficulty(
                courseId, QuestionStatus.ACTIVE, QuestionDifficulty.EASY);
    }

    @Test
    void list_noFilter_returnsAll() {
        when(bankQuestionRepository.findByCourseId(courseId)).thenReturn(List.of());

        service.list(courseId, null, null, null);

        verify(bankQuestionRepository).findByCourseId(courseId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BankQuestionRequest buildRequest(QuestionType type, List<BankOptionRequest> options) {
        BankQuestionRequest r = new BankQuestionRequest();
        r.setQuestionText("Câu hỏi test?");
        r.setQuestionType(type);
        r.setDifficulty(QuestionDifficulty.EASY);
        r.setOptions(options);
        return r;
    }

    private BankOptionRequest option(String text, boolean correct) {
        BankOptionRequest o = new BankOptionRequest();
        o.setOptionText(text);
        o.setIsCorrect(correct);
        return o;
    }

    private BankQuestionEntity buildEntity() {
        BankQuestionEntity q = new BankQuestionEntity();
        q.setId(questionId);
        q.setCourseId(courseId);
        q.setQuestionText("Test?");
        q.setQuestionType(QuestionType.SINGLE_CHOICE);
        q.setDifficulty(QuestionDifficulty.MEDIUM);
        q.setStatus(QuestionStatus.ACTIVE);
        return q;
    }
}
