package project.lms_rikkei_edu.modules.assignment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.StudentAssignmentListResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.SubmissionResponse;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;
import project.lms_rikkei_edu.modules.assignment.service.StudentAssignmentService;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentAssignmentControllerTest {

    private StudentAssignmentService studentAssignmentService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    private final UUID courseId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        studentAssignmentService = mock(StudentAssignmentService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StudentAssignmentController(studentAssignmentService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllAssignments_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        when(studentAssignmentService.getAllAssignments(studentId))
                .thenReturn(List.of(assignmentListResponse()));

        mockMvc.perform(get("/api/student/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(assignmentId.toString()))
                .andExpect(jsonPath("$[0].courseTitle").value("Course Title"));
    }

    @Test
    void getAllAssignments_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/student/assignments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAssignments_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        when(studentAssignmentService.getAssignments(courseId, studentId))
                .thenReturn(List.of(assignmentListResponse()));

        mockMvc.perform(get("/api/student/courses/{courseId}/assignments", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(assignmentId.toString()))
                .andExpect(jsonPath("$[0].title").value("Test Assignment"));
    }

    @Test
    void getAssignments_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/student/courses/{courseId}/assignments", courseId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAssignmentDetail_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        when(studentAssignmentService.getAssignmentDetail(courseId, assignmentId, studentId))
                .thenReturn(detailResponse());

        mockMvc.perform(get("/api/student/courses/{courseId}/assignments/{assignmentId}", courseId, assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.title").value("Test Assignment"));
    }

    @Test
    void getAssignmentDetail_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/student/courses/{courseId}/assignments/{assignmentId}", courseId, assignmentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitAssignment_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        when(studentAssignmentService.submitAssignment(eq(courseId), eq(assignmentId), eq(studentId),
                any(), any())).thenReturn(submissionResponse());

        var file = new MockMultipartFile("files", "hw.pdf", "application/pdf", new byte[1024]);

        mockMvc.perform(multipart("/api/student/courses/{courseId}/assignments/{assignmentId}/submit",
                        courseId, assignmentId)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void submitAssignment_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        var file = new MockMultipartFile("files", "hw.pdf", "application/pdf", new byte[1024]);

        mockMvc.perform(multipart("/api/student/courses/{courseId}/assignments/{assignmentId}/submit",
                        courseId, assignmentId)
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    private StudentAssignmentListResponse assignmentListResponse() {
        return StudentAssignmentListResponse.builder()
                .id(assignmentId)
                .courseId(courseId)
                .courseTitle("Course Title")
                .title("Test Assignment")
                .status(AssignmentStatus.PUBLISHED)
                .maxScore(BigDecimal.TEN)
                .deadline(OffsetDateTime.now().plusHours(48))
                .build();
    }

    private StudentAssignmentDetailResponse detailResponse() {
        return StudentAssignmentDetailResponse.builder()
                .id(assignmentId)
                .courseId(courseId)
                .title("Test Assignment")
                .status(AssignmentStatus.PUBLISHED)
                .maxScore(BigDecimal.TEN)
                .deadline(OffsetDateTime.now().plusHours(48))
                .build();
    }

    private SubmissionResponse submissionResponse() {
        return SubmissionResponse.builder()
                .id(UUID.randomUUID())
                .status("SUBMITTED")
                .isLate(false)
                .submittedAt(OffsetDateTime.now())
                .files(List.of())
                .build();
    }
}
