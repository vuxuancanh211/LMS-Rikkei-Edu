package project.lms_rikkei_edu.modules.quiz.service;

import project.lms_rikkei_edu.modules.quiz.dto.response.AttemptHistoryEntry;
import project.lms_rikkei_edu.modules.quiz.dto.response.QuizStatsResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.StudentQuizProgressEntry;

import java.util.List;
import java.util.UUID;

public interface QuizStatsService {

    // Instructor: thống kê tổng quan cho 1 quiz (attempt count, avg score, pass rate, per-question)
    QuizStatsResponse getQuizStats(UUID courseId, UUID quizId);

    // Student: lịch sử các lần thi của mình trên 1 quiz
    List<AttemptHistoryEntry> getStudentAttemptHistory(UUID courseId, UUID quizId, UUID studentId);

    // Student: tiến độ học của mình trong 1 khóa (tất cả quiz)
    List<StudentQuizProgressEntry> getStudentCourseProgress(UUID courseId, UUID studentId);

    // Instructor: lịch sử tất cả attempt của 1 quiz (xem theo student)
    List<AttemptHistoryEntry> getAllAttemptsForQuiz(UUID courseId, UUID quizId);
}
