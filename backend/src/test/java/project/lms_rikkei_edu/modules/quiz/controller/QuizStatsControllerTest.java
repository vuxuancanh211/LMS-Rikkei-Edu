package project.lms_rikkei_edu.modules.quiz.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CourseOwnershipGuard;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.response.QuizStatsResponse;
import project.lms_rikkei_edu.modules.quiz.service.QuizStatsService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizStatsControllerTest {

    private QuizStatsService statsService;
    private CurrentUserProvider currentUserProvider;
    private CourseOwnershipGuard ownershipGuard;
    private MockMvc mockMvc;

    private final UUID courseId = UUID.randomUUID();
    private final UUID quizId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        statsService = mock(QuizStatsService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        ownershipGuard = mock(CourseOwnershipGuard.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuizStatsController(statsService, currentUserProvider, ownershipGuard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getQuizStats_checksOwnership_returnsOk() throws Exception {
        doNothing().when(ownershipGuard).requireOwnership(courseId);
        when(statsService.getQuizStats(courseId, quizId))
                .thenReturn(QuizStatsResponse.builder().quizId(quizId).totalAttempts(5).build());

        mockMvc.perform(get("/api/courses/{courseId}/quizzes/{quizId}/stats", courseId, quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempts").value(5));

        verify(ownershipGuard).requireOwnership(courseId);
    }

    @Test
    void getAllAttempts_checksOwnership_returnsOk() throws Exception {
        doNothing().when(ownershipGuard).requireOwnership(courseId);
        when(statsService.getAllAttemptsForQuiz(courseId, quizId)).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/{courseId}/quizzes/{quizId}/attempts", courseId, quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getMyAttempts_returnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        when(statsService.getStudentAttemptHistory(courseId, quizId, studentId)).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/{courseId}/quizzes/{quizId}/my-attempts", courseId, quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getMyCourseProgress_returnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        when(statsService.getStudentCourseProgress(courseId, studentId)).thenReturn(List.of());

        mockMvc.perform(get("/api/courses/{courseId}/my-quiz-progress", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
