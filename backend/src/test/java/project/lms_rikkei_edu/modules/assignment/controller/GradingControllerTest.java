package project.lms_rikkei_edu.modules.assignment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.assignment.dto.request.BatchReleaseRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.GradeRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.InstructorSubmissionResponse;
import project.lms_rikkei_edu.modules.assignment.service.GradingService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GradingControllerTest {

    private GradingService gradingService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID courseId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        gradingService = mock(GradingService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GradingController(gradingService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void getSubmissions_withAllParams_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(gradingService.getSubmissions(courseId, assignmentId, instructorId, "SUBMITTED"))
                .thenReturn(List.of(submissionResponse()));

        mockMvc.perform(get("/api/instructor/submissions")
                        .param("courseId", courseId.toString())
                        .param("assignmentId", assignmentId.toString())
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(submissionId.toString()))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    @Test
    void getSubmissions_withoutParams_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(gradingService.getSubmissions(null, null, instructorId, "ALL"))
                .thenReturn(List.of(submissionResponse()));

        mockMvc.perform(get("/api/instructor/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(submissionId.toString()));
    }

    @Test
    void getSubmissions_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/instructor/submissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gradeSubmission_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(gradingService.gradeSubmission(any(GradeRequest.class), eq(instructorId)))
                .thenReturn(submissionResponse());

        var request = new GradeRequest();
        request.setSubmissionId(submissionId);
        request.setScore(BigDecimal.valueOf(85));
        request.setFeedback("Good");

        mockMvc.perform(patch("/api/instructor/submissions/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId.toString()));
    }

    @Test
    void gradeSubmission_invalidBody_returns400() throws Exception {
        mockMvc.perform(patch("/api/instructor/submissions/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gradeSubmission_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        var request = new GradeRequest();
        request.setSubmissionId(submissionId);

        mockMvc.perform(patch("/api/instructor/submissions/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchReleaseScores_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));

        var request = new BatchReleaseRequest();
        request.setSubmissionIds(List.of(submissionId));

        mockMvc.perform(patch("/api/instructor/submissions/batch/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void batchReleaseScores_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        var request = new BatchReleaseRequest();
        request.setSubmissionIds(List.of(submissionId));

        mockMvc.perform(patch("/api/instructor/submissions/batch/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnSubmission_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));

        mockMvc.perform(patch("/api/instructor/submissions/{submissionId}/return", submissionId))
                .andExpect(status().isOk());
    }

    @Test
    void returnSubmission_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/instructor/submissions/{submissionId}/return", submissionId))
                .andExpect(status().isUnauthorized());
    }

    private InstructorSubmissionResponse submissionResponse() {
        return InstructorSubmissionResponse.builder()
                .id(submissionId)
                .status("SUBMITTED")
                .isLate(false)
                .studentId(UUID.randomUUID())
                .studentName("Student A")
                .assignmentId(assignmentId)
                .assignmentTitle("Test Assignment")
                .assignmentMaxScore(BigDecimal.TEN)
                .courseId(courseId)
                .courseTitle("Course Title")
                .submittedAt(OffsetDateTime.now())
                .files(List.of())
                .build();
    }
}
