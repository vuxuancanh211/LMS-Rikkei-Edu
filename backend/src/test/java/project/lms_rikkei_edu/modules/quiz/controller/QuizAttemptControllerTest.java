package project.lms_rikkei_edu.modules.quiz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.AutosaveRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.SubmitAttemptRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AttemptResultResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.StartAttemptResponse;
import project.lms_rikkei_edu.modules.quiz.enums.AttemptStatus;
import project.lms_rikkei_edu.modules.quiz.service.QuizAttemptService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizAttemptControllerTest {

    private QuizAttemptService attemptService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID courseId = UUID.randomUUID();
    private final UUID quizId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        attemptService = mock(QuizAttemptService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuizAttemptController(attemptService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
    }

    @Test
    void startAttempt_returnsOk() throws Exception {
        StartAttemptResponse response = StartAttemptResponse.builder()
                .attemptId(attemptId).quizId(quizId).attemptNumber(1).build();
        when(attemptService.startAttempt(eq(courseId), eq(quizId), eq(studentId), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/courses/{courseId}/quizzes/{quizId}/attempts", courseId, quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId.toString()));
    }

    @Test
    void autosave_returnsNoContent() throws Exception {
        AutosaveRequest request = new AutosaveRequest();
        request.setAnswers(Map.of());

        mockMvc.perform(put("/api/courses/{courseId}/quizzes/{quizId}/attempts/{attemptId}/autosave",
                        courseId, quizId, attemptId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(attemptService).autosave(eq(attemptId), eq(studentId), any());
    }

    @Test
    void submit_returnsOk() throws Exception {
        AttemptResultResponse response = AttemptResultResponse.builder()
                .attemptId(attemptId).quizId(quizId).status(AttemptStatus.GRADED).build();
        when(attemptService.submit(eq(attemptId), eq(studentId), any())).thenReturn(response);

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setAnswers(Map.of());

        mockMvc.perform(post("/api/courses/{courseId}/quizzes/{quizId}/attempts/{attemptId}/submit",
                        courseId, quizId, attemptId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRADED"));
    }

    @Test
    void getResult_returnsOk() throws Exception {
        AttemptResultResponse response = AttemptResultResponse.builder()
                .attemptId(attemptId).quizId(quizId).status(AttemptStatus.GRADED).build();
        when(attemptService.getResult(attemptId, studentId)).thenReturn(response);

        mockMvc.perform(get("/api/courses/{courseId}/quizzes/{quizId}/attempts/{attemptId}/result",
                        courseId, quizId, attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId.toString()));
    }
}
