package project.lms_rikkei_edu.modules.quiz.service;

import project.lms_rikkei_edu.modules.quiz.dto.request.AutosaveRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.SubmitAttemptRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AttemptResultResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.StartAttemptResponse;

import java.util.UUID;

public interface QuizAttemptService {

    StartAttemptResponse startAttempt(UUID courseId, UUID quizId, UUID studentId, String ipAddress);

    void autosave(UUID attemptId, UUID studentId, AutosaveRequest request);

    AttemptResultResponse submit(UUID attemptId, UUID studentId, SubmitAttemptRequest request);

    AttemptResultResponse getResult(UUID attemptId, UUID requesterId);

    // Scheduler gọi — auto-submit attempt hết giờ
    void autoSubmitExpiredAttempts();
}
