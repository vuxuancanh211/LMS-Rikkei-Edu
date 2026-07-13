package project.lms_rikkei_edu.modules.quiz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CourseOwnershipGuard;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.*;
import project.lms_rikkei_edu.modules.quiz.dto.response.*;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;
import project.lms_rikkei_edu.modules.quiz.enums.QuizType;
import project.lms_rikkei_edu.modules.quiz.service.QuizService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizControllerTest {

    private QuizService quizService;
    private CurrentUserProvider currentUserProvider;
    private CourseOwnershipGuard ownershipGuard;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID courseId = UUID.randomUUID();
    private final UUID quizId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        quizService = mock(QuizService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        ownershipGuard = mock(CourseOwnershipGuard.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuizController(quizService, currentUserProvider, ownershipGuard))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        doNothing().when(ownershipGuard).requireOwnership(courseId);
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
    }

    private QuizMetadataRequest buildMetadataRequest() {
        QuizMetadataRequest req = new QuizMetadataRequest();
        req.setTitle("Quiz 1");
        req.setQuizType(QuizType.STATIC);
        return req;
    }

    @Test
    void list_returnsPage() throws Exception {
        Page<QuizSummaryResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(quizService.listByCourse(eq(courseId), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/courses/{courseId}/quizzes", courseId))
                .andExpect(status().isOk());
    }

    @Test
    void getDetail_returnsQuiz() throws Exception {
        when(quizService.getDetail(courseId, quizId))
                .thenReturn(QuizDetailResponse.builder().id(quizId).build());

        mockMvc.perform(get("/api/courses/{courseId}/quizzes/{quizId}", courseId, quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(quizId.toString()));
    }

    @Test
    void create_checksOwnership_returnsQuiz() throws Exception {
        when(quizService.create(eq(courseId), eq(instructorId), any()))
                .thenReturn(QuizSummaryResponse.builder().id(quizId).build());

        mockMvc.perform(post("/api/courses/{courseId}/quizzes", courseId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildMetadataRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(quizId.toString()));

        verify(ownershipGuard).requireOwnership(courseId);
    }

    @Test
    void updateMetadata_returnsUpdatedQuiz() throws Exception {
        when(quizService.updateMetadata(eq(courseId), eq(quizId), any()))
                .thenReturn(QuizSummaryResponse.builder().id(quizId).build());

        mockMvc.perform(put("/api/courses/{courseId}/quizzes/{quizId}", courseId, quizId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildMetadataRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/courses/{courseId}/quizzes/{quizId}", courseId, quizId))
                .andExpect(status().isNoContent());

        verify(quizService).delete(courseId, quizId);
    }

    @Test
    void addBankQuestions_returnsDetail() throws Exception {
        when(quizService.addBankQuestions(eq(courseId), eq(quizId), any()))
                .thenReturn(QuizDetailResponse.builder().id(quizId).build());

        QuizAddBankQuestionsRequest request = new QuizAddBankQuestionsRequest();
        request.setBankQuestionIds(List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/courses/{courseId}/quizzes/{quizId}/questions/bank", courseId, quizId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void addManualQuestion_returnsDetail() throws Exception {
        when(quizService.addManualQuestion(eq(courseId), eq(quizId), eq(instructorId), any()))
                .thenReturn(QuizDetailResponse.builder().id(quizId).build());

        BankOptionRequest option = new BankOptionRequest();
        option.setOptionText("A");
        option.setIsCorrect(true);
        QuizManualQuestionRequest request = new QuizManualQuestionRequest();
        request.setQuestionText("2+2=?");
        request.setQuestionType(QuestionType.SINGLE_CHOICE);
        request.setDifficulty(QuestionDifficulty.EASY);
        request.setOptions(List.of(option));

        mockMvc.perform(post("/api/courses/{courseId}/quizzes/{quizId}/questions/manual", courseId, quizId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void removeQuestion_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/courses/{courseId}/quizzes/{quizId}/questions/{questionId}",
                        courseId, quizId, questionId))
                .andExpect(status().isNoContent());

        verify(quizService).removeQuestion(courseId, quizId, questionId);
    }

    @Test
    void reorderQuestions_returnsDetail() throws Exception {
        List<UUID> ids = List.of(questionId);
        when(quizService.reorderQuestions(courseId, quizId, ids))
                .thenReturn(QuizDetailResponse.builder().id(quizId).build());

        mockMvc.perform(put("/api/courses/{courseId}/quizzes/{quizId}/questions/reorder", courseId, quizId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk());
    }

    @Test
    void configureRandomDraw_returnsSummary() throws Exception {
        when(quizService.configureRandomDraw(eq(courseId), eq(quizId), any()))
                .thenReturn(QuizSummaryResponse.builder().id(quizId).build());

        QuizRandomConfigRequest request = new QuizRandomConfigRequest();
        request.setRandomMode(project.lms_rikkei_edu.modules.quiz.enums.RandomMode.FULLY_RANDOM);
        request.setTotalCount(10);

        mockMvc.perform(put("/api/courses/{courseId}/quizzes/{quizId}/random-config", courseId, quizId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void publish_returnsSummary() throws Exception {
        when(quizService.publish(courseId, quizId)).thenReturn(QuizSummaryResponse.builder().id(quizId).build());

        mockMvc.perform(post("/api/courses/{courseId}/quizzes/{quizId}/publish", courseId, quizId))
                .andExpect(status().isOk());
    }

    @Test
    void archive_returnsSummary() throws Exception {
        when(quizService.archive(courseId, quizId)).thenReturn(QuizSummaryResponse.builder().id(quizId).build());

        mockMvc.perform(post("/api/courses/{courseId}/quizzes/{quizId}/archive", courseId, quizId))
                .andExpect(status().isOk());
    }

    @Test
    void unarchive_returnsSummary() throws Exception {
        when(quizService.unarchive(courseId, quizId)).thenReturn(QuizSummaryResponse.builder().id(quizId).build());

        mockMvc.perform(post("/api/courses/{courseId}/quizzes/{quizId}/unarchive", courseId, quizId))
                .andExpect(status().isOk());
    }

    @Test
    void dryRun_returnsResponse() throws Exception {
        when(quizService.dryRun(courseId, quizId)).thenReturn(DryRunResponse.builder().totalQuestions(3).build());

        mockMvc.perform(get("/api/courses/{courseId}/quizzes/{quizId}/dry-run", courseId, quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(3));
    }

    @Test
    void gradeDryRun_returnsResponse() throws Exception {
        when(quizService.gradeDryRun(eq(courseId), eq(quizId), any()))
                .thenReturn(DryRunGradeResponse.builder().correctCount(1).build());

        DryRunGradeRequest request = new DryRunGradeRequest();
        request.setQuestionIds(List.of(questionId));

        mockMvc.perform(post("/api/courses/{courseId}/quizzes/{quizId}/dry-run/grade", courseId, quizId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctCount").value(1));
    }
}
