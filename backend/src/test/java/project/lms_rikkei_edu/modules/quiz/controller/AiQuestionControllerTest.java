package project.lms_rikkei_edu.modules.quiz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CourseOwnershipGuard;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.AiGenerateQuestionsRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankOptionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.request.BankQuestionRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.AiGenerationJobStatusResponse;
import project.lms_rikkei_edu.modules.quiz.dto.response.BankQuestionResponse;
import project.lms_rikkei_edu.modules.quiz.enums.GenerationStep;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionType;
import project.lms_rikkei_edu.modules.quiz.service.AiQuestionGeneratorService;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiQuestionControllerTest {

    private AiQuestionGeneratorService aiGeneratorService;
    private BankQuestionService bankQuestionService;
    private CurrentUserProvider currentUserProvider;
    private CourseOwnershipGuard ownershipGuard;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aiGeneratorService = mock(AiQuestionGeneratorService.class);
        bankQuestionService = mock(BankQuestionService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        ownershipGuard = mock(CourseOwnershipGuard.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiQuestionController(
                        aiGeneratorService, bankQuestionService, currentUserProvider, ownershipGuard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    private AiGenerateQuestionsRequest buildGenerateRequest() {
        AiGenerateQuestionsRequest req = new AiGenerateQuestionsRequest();
        req.setTopic("Indexing");
        req.setQuestionType(QuestionType.SINGLE_CHOICE);
        req.setDifficulty(QuestionDifficulty.EASY);
        return req;
    }

    @Test
    void generate_startsJobAndTriggersAsync() throws Exception {
        doNothing().when(ownershipGuard).requireOwnership(courseId);
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(aiGeneratorService.startGenerate(eq(courseId), any(), eq(instructorId))).thenReturn(jobId);

        mockMvc.perform(post("/api/courses/{courseId}/bank-questions/ai/generate", courseId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildGenerateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));

        verify(aiGeneratorService).generateAsync(eq(jobId), eq(courseId), any());
        verify(ownershipGuard).requireOwnership(courseId);
    }

    @Test
    void generateStatus_returnsJobStatus() throws Exception {
        doNothing().when(ownershipGuard).requireOwnership(courseId);
        when(aiGeneratorService.getJobStatus(jobId))
                .thenReturn(AiGenerationJobStatusResponse.builder().step(GenerationStep.DONE).build());

        mockMvc.perform(get("/api/courses/{courseId}/bank-questions/ai/generate/{jobId}", courseId, jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("DONE"));
    }

    @Test
    void save_createsEachQuestionInBank() throws Exception {
        doNothing().when(ownershipGuard).requireOwnership(courseId);
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));

        BankOptionRequest option = new BankOptionRequest();
        option.setOptionText("A");
        option.setIsCorrect(true);
        BankQuestionRequest question = new BankQuestionRequest();
        question.setQuestionText("2+2=?");
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setDifficulty(QuestionDifficulty.EASY);
        question.setOptions(List.of(option));

        when(bankQuestionService.create(eq(courseId), eq(instructorId), any()))
                .thenReturn(BankQuestionResponse.builder().id(UUID.randomUUID()).build());

        mockMvc.perform(post("/api/courses/{courseId}/bank-questions/ai/save", courseId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(question))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
