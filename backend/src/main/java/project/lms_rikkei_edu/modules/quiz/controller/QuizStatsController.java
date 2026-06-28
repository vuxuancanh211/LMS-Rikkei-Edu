package project.lms_rikkei_edu.modules.quiz.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.response.AttemptHistoryEntry;
import project.lms_rikkei_edu.modules.quiz.dto.response.QuizStatsResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.StudentQuizProgressEntry;
import project.lms_rikkei_edu.modules.quiz.service.QuizStatsService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QuizStatsController {

    private final QuizStatsService statsService;
    private final CurrentUserProvider currentUserProvider;

    // Instructor: thống kê tổng quan quiz
    @GetMapping("/api/courses/{courseId}/quizzes/{quizId}/stats")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizStatsResponse> getQuizStats(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId) {
        return ResponseEntity.ok(statsService.getQuizStats(courseId, quizId));
    }

    // Instructor: tất cả attempt của 1 quiz
    @GetMapping("/api/courses/{courseId}/quizzes/{quizId}/attempts")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<AttemptHistoryEntry>> getAllAttempts(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId) {
        return ResponseEntity.ok(statsService.getAllAttemptsForQuiz(courseId, quizId));
    }

    // Student: lịch sử lần thi của mình trên 1 quiz
    @GetMapping("/api/courses/{courseId}/quizzes/{quizId}/my-attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<AttemptHistoryEntry>> getMyAttempts(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId) {
        UUID studentId = resolveCurrentUser();
        return ResponseEntity.ok(statsService.getStudentAttemptHistory(courseId, quizId, studentId));
    }

    // Student: tiến độ học toàn khóa
    @GetMapping("/api/courses/{courseId}/my-quiz-progress")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentQuizProgressEntry>> getMyCourseProgress(
            @PathVariable UUID courseId) {
        UUID studentId = resolveCurrentUser();
        return ResponseEntity.ok(statsService.getStudentCourseProgress(courseId, studentId));
    }

    private UUID resolveCurrentUser() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Không xác định được người dùng"));
    }
}
