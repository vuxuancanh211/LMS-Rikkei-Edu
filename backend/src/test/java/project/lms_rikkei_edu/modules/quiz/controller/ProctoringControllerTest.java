package project.lms_rikkei_edu.modules.quiz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.quiz.dto.request.ViolationRequest;
import project.lms_rikkei_edu.modules.quiz.dto.response.ViolationResponse;
import project.lms_rikkei_edu.modules.quiz.enums.ViolationType;
import project.lms_rikkei_edu.modules.quiz.service.ProctoringService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProctoringControllerTest {

    private ProctoringService proctoringService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID attemptId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        proctoringService = mock(ProctoringService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProctoringController(proctoringService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void reportViolation_returnsOk() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        ViolationResponse response = ViolationResponse.builder()
                .id(UUID.randomUUID()).attemptId(attemptId)
                .violationType(ViolationType.TAB_SWITCH)
                .violationOrder(1).totalViolations(1).maxViolations(3)
                .actionTaken("WARNED").lockedOut(false)
                .build();
        when(proctoringService.reportViolation(eq(attemptId), eq(studentId), any())).thenReturn(response);

        ViolationRequest request = new ViolationRequest();
        request.setViolationType(ViolationType.TAB_SWITCH);

        mockMvc.perform(post("/api/attempts/{attemptId}/proctoring/violations", attemptId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionTaken").value("WARNED"));
    }

    @Test
    void reportViolation_unauthenticated_returns400() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        ViolationRequest request = new ViolationRequest();
        request.setViolationType(ViolationType.TAB_SWITCH);

        mockMvc.perform(post("/api/attempts/{attemptId}/proctoring/violations", attemptId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getViolations_returnsList() throws Exception {
        UUID instructorId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(proctoringService.getViolations(attemptId, instructorId)).thenReturn(List.of());

        mockMvc.perform(get("/api/attempts/{attemptId}/proctoring/violations", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
