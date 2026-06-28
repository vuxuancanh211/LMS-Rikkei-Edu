package project.lms_rikkei_edu.modules.quiz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.quiz.dto.request.ViolationRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.ViolationResponse;
import project.lms_rikkei_edu.modules.quiz.entity.ProctoringViolationLogEntity;
import project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptEntity;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;
import project.lms_rikkei_edu.modules.quiz.enums.ViolationType;
import project.lms_rikkei_edu.modules.quiz.repository.ProctoringViolationLogRepository;
import project.lms_rikkei_edu.modules.quiz.repository.QuizAttemptRepository;
import project.lms_rikkei_edu.modules.quiz.service.impl.ProctoringServiceImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProctoringServiceTest {

    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private ProctoringViolationLogRepository violationRepository;
    @Mock private QuizAttemptService attemptService;
    @Mock private SseEmitterRegistry sseRegistry;

    @InjectMocks
    private ProctoringServiceImpl proctoringService;

    private UUID attemptId, studentId;

    @BeforeEach
    void setUp() {
        attemptId = UUID.randomUUID();
        studentId = UUID.randomUUID();
    }

    // ── reportViolation ───────────────────────────────────────────────────────

    @Test
    void reportViolation_firstViolation_sendsWarning() {
        QuizAttemptEntity attempt = buildAttempt(true);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(violationRepository.countByAttemptId(attemptId)).thenReturn(0L);
        when(violationRepository.save(any())).thenAnswer(inv -> {
            ProctoringViolationLogEntity v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
        when(attemptRepository.save(any())).thenReturn(attempt);

        ViolationResponse result = proctoringService.reportViolation(attemptId, studentId, buildRequest());

        assertThat(result.getViolationOrder()).isEqualTo(1);
        assertThat(result.getActionTaken()).isEqualTo("WARNED");
        assertThat(result.isLockedOut()).isFalse();
        verify(sseRegistry).sendToUser(eq(studentId), eq("proctoring_warning"), any());
        verify(attemptService, never()).submit(any(), any(), any());
    }

    @Test
    void reportViolation_secondViolation_sendsWarning() {
        QuizAttemptEntity attempt = buildAttempt(true);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(violationRepository.countByAttemptId(attemptId)).thenReturn(1L);
        when(violationRepository.save(any())).thenAnswer(inv -> {
            ProctoringViolationLogEntity v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
        when(attemptRepository.save(any())).thenReturn(attempt);

        ViolationResponse result = proctoringService.reportViolation(attemptId, studentId, buildRequest());

        assertThat(result.getViolationOrder()).isEqualTo(2);
        assertThat(result.isLockedOut()).isFalse();
        verify(sseRegistry).sendToUser(eq(studentId), eq("proctoring_warning"), any());
    }

    @Test
    void reportViolation_thirdViolation_locksOutAndAutoSubmits() {
        QuizAttemptEntity attempt = buildAttempt(true);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(violationRepository.countByAttemptId(attemptId)).thenReturn(2L);
        when(violationRepository.save(any())).thenAnswer(inv -> {
            ProctoringViolationLogEntity v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
        when(attemptRepository.save(any())).thenReturn(attempt);
        when(attemptService.submit(any(), any(), any())).thenReturn(null);

        ViolationResponse result = proctoringService.reportViolation(attemptId, studentId, buildRequest());

        assertThat(result.getViolationOrder()).isEqualTo(3);
        assertThat(result.getActionTaken()).isEqualTo("AUTO_SUBMITTED");
        assertThat(result.isLockedOut()).isTrue();
        verify(sseRegistry).sendToUser(eq(studentId), eq("proctoring_lockout"), any());
        verify(attemptService).submit(eq(attemptId), eq(studentId), any());
    }

    @Test
    void reportViolation_proctoringDisabled_throwsException() {
        QuizAttemptEntity attempt = buildAttempt(false); // proctoring off
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() ->
                proctoringService.reportViolation(attemptId, studentId, buildRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Proctoring không được bật");
    }

    @Test
    void reportViolation_attemptNotInProgress_throwsException() {
        QuizAttemptEntity attempt = buildAttempt(true);
        attempt.setStatus(AttemptStatus.GRADED);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() ->
                proctoringService.reportViolation(attemptId, studentId, buildRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được nộp");
    }

    @Test
    void reportViolation_wrongStudent_throwsException() {
        QuizAttemptEntity attempt = buildAttempt(true);
        attempt.setStudentId(UUID.randomUUID()); // khác studentId
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() ->
                proctoringService.reportViolation(attemptId, studentId, buildRequest()))
                .isInstanceOf(BusinessException.class);
    }

    // ── getViolations ─────────────────────────────────────────────────────────

    @Test
    void getViolations_returnsListForAttempt() {
        QuizAttemptEntity attempt = buildAttempt(true);
        ProctoringViolationLogEntity v1 = buildViolationLog(1);
        ProctoringViolationLogEntity v2 = buildViolationLog(2);

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(violationRepository.findByAttemptIdOrderByViolationOrder(attemptId))
                .thenReturn(List.of(v1, v2));
        when(violationRepository.countByAttemptId(attemptId)).thenReturn(2L);

        List<ViolationResponse> result = proctoringService.getViolations(attemptId, UUID.randomUUID());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getViolationOrder()).isEqualTo(1);
        assertThat(result.get(1).getViolationOrder()).isEqualTo(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QuizAttemptEntity buildAttempt(boolean proctoringEnabled) {
        QuizAttemptEntity a = new QuizAttemptEntity();
        a.setId(attemptId);
        a.setStudentId(studentId);
        a.setStatus(AttemptStatus.IN_PROGRESS);
        a.setProctoringEnabled(proctoringEnabled);
        a.setViolationCount(0);
        return a;
    }

    private ViolationRequest buildRequest() {
        ViolationRequest req = new ViolationRequest();
        req.setViolationType(ViolationType.TAB_SWITCH);
        req.setDescription("Học viên chuyển tab");
        return req;
    }

    private ProctoringViolationLogEntity buildViolationLog(int order) {
        ProctoringViolationLogEntity v = new ProctoringViolationLogEntity();
        v.setId(UUID.randomUUID());
        v.setAttemptId(attemptId);
        v.setStudentId(studentId);
        v.setViolationType(ViolationType.TAB_SWITCH);
        v.setViolationOrder(order);
        v.setActionTaken(order >= 3 ? "AUTO_SUBMITTED" : "WARNED");
        return v;
    }
}
