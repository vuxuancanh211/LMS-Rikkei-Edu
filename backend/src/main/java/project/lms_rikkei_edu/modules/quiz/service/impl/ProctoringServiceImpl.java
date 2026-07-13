package project.lms_rikkei_edu.modules.quiz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.sse.SseEmitterRegistry;
import project.lms_rikkei_edu.modules.quiz.dto.request.SubmitAttemptRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.ViolationRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.ViolationResponse;
import project.lms_rikkei_edu.modules.quiz.entity.ProctoringViolationLogEntity;
import project.lms_rikkei_edu.modules.quiz.entity.QuizAttemptEntity;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;
import project.lms_rikkei_edu.modules.quiz.repository.ProctoringViolationLogRepository;
import project.lms_rikkei_edu.modules.quiz.repository.QuizAttemptRepository;
import project.lms_rikkei_edu.modules.quiz.service.ProctoringService;
import project.lms_rikkei_edu.modules.quiz.service.QuizAttemptService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProctoringServiceImpl implements ProctoringService {

    private static final int MAX_VIOLATIONS = 3;

    private final QuizAttemptRepository attemptRepository;
    private final ProctoringViolationLogRepository violationRepository;
    private final QuizAttemptService attemptService;
    private final SseEmitterRegistry sseRegistry;

    @Override
    @Transactional
    public ViolationResponse reportViolation(UUID attemptId, UUID studentId, ViolationRequest request) {
        QuizAttemptEntity attempt = findInProgressAttempt(attemptId, studentId);

        if (!Boolean.TRUE.equals(attempt.getProctoringEnabled()))
            throw new BusinessException("Proctoring không được bật cho bài thi này");

        long prevCount = violationRepository.countByAttemptId(attemptId);
        int newOrder = (int) prevCount + 1;

        // Persist vi phạm
        ProctoringViolationLogEntity violation = new ProctoringViolationLogEntity();
        violation.setAttemptId(attemptId);
        violation.setStudentId(studentId);
        violation.setViolationType(request.getViolationType());
        violation.setViolationOrder(newOrder);
        violation.setDescription(request.getDescription());
        violation.setClientTimestamp(request.getClientTimestamp());

        boolean lockedOut = newOrder >= MAX_VIOLATIONS;
        violation.setActionTaken(lockedOut ? "AUTO_SUBMITTED" : "WARNED");
        violationRepository.save(violation);

        // Cập nhật violation_count trên attempt
        attempt.setViolationCount(newOrder);
        attemptRepository.save(attempt);

        if (lockedOut) {
            // SSE: thông báo bị khóa
            sseRegistry.sendToUser(studentId, "proctoring_lockout", Map.of(
                    "attemptId", attemptId,
                    "message", "Bạn đã vi phạm " + MAX_VIOLATIONS + " lần và bị tự động nộp bài"
            ));
            // Auto-submit
            attemptService.submit(attemptId, studentId, new SubmitAttemptRequest());
            log.info("Proctoring lockout — auto-submitted attempt {} for student {}", attemptId, studentId);
        } else {
            // SSE: cảnh báo
            sseRegistry.sendToUser(studentId, "proctoring_warning", Map.of(
                    "attemptId", attemptId,
                    "violationCount", newOrder,
                    "maxViolations", MAX_VIOLATIONS,
                    "message", "Cảnh báo vi phạm " + newOrder + "/" + MAX_VIOLATIONS
            ));
        }

        return ViolationResponse.builder()
                .id(violation.getId())
                .attemptId(attemptId)
                .violationType(request.getViolationType())
                .violationOrder(newOrder)
                .totalViolations(newOrder)
                .maxViolations(MAX_VIOLATIONS)
                .actionTaken(violation.getActionTaken())
                .lockedOut(lockedOut)
                .serverTimestamp(violation.getServerTimestamp())
                .build();
    }

    @Override
    public List<ViolationResponse> getViolations(UUID attemptId, UUID requesterId) {
        QuizAttemptEntity attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Attempt không tồn tại", HttpStatus.NOT_FOUND));

        // Student chỉ được xem của mình; instructor/admin không bị chặn ở đây (check ở controller)
        return violationRepository.findByAttemptIdOrderByViolationOrder(attemptId)
                .stream()
                .map(v -> ViolationResponse.builder()
                        .id(v.getId())
                        .attemptId(v.getAttemptId())
                        .violationType(v.getViolationType())
                        .violationOrder(v.getViolationOrder())
                        .totalViolations((int) violationRepository.countByAttemptId(attemptId))
                        .maxViolations(MAX_VIOLATIONS)
                        .actionTaken(v.getActionTaken())
                        .lockedOut("AUTO_SUBMITTED".equals(v.getActionTaken()))
                        .serverTimestamp(v.getServerTimestamp())
                        .build())
                .toList();
    }

    private QuizAttemptEntity findInProgressAttempt(UUID attemptId, UUID studentId) {
        QuizAttemptEntity attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Attempt không tồn tại", HttpStatus.NOT_FOUND));
        if (!attempt.getStudentId().equals(studentId))
            throw new BusinessException("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS)
            throw new BusinessException("Bài làm đã được nộp hoặc hết hạn");
        return attempt;
    }
}
