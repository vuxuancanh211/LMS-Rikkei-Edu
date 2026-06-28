package project.lms_rikkei_edu.modules.quiz.service;

import project.lms_rikkei_edu.modules.quiz.dto.request.ViolationRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.ViolationResponse;

import java.util.List;
import java.util.UUID;

public interface ProctoringService {

    // Student báo cáo vi phạm — trả về kết quả (còn tiếp hay bị lock)
    ViolationResponse reportViolation(UUID attemptId, UUID studentId, ViolationRequest request);

    // Lấy lịch sử vi phạm của 1 attempt — cho instructor xem
    List<ViolationResponse> getViolations(UUID attemptId, UUID requesterId);
}
