package project.lms_rikkei_edu.modules.assignment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.assignment.dto.request.CreateAssignmentRequest;
import project.lms_rikkei_edu.modules.assignment.dto.request.UpdateAssignmentRequest;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentAttachmentResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentDetailResponse;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentResponse;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;
import project.lms_rikkei_edu.modules.assignment.exception.AssignmentNotFoundException;
import project.lms_rikkei_edu.modules.assignment.service.AssignmentService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstructorAssignmentControllerTest {

    private AssignmentService assignmentService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID courseId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assignmentService = mock(AssignmentService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InstructorAssignmentController(assignmentService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void createAssignment_returns201() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.createAssignment(any(), any(), any())).thenReturn(assignmentResponse());

        var request = new CreateAssignmentRequest();
        request.setTitle("Valid Title");
        request.setScope(AssignmentScope.ALL_GROUPS);

        mockMvc.perform(post("/api/instructor/courses/{courseId}/assignments", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()));
    }

    @Test
    void createAssignment_validationError_returns400() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));

        mockMvc.perform(post("/api/instructor/courses/{courseId}/assignments", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAssignments_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.getAssignments(courseId, instructorId))
                .thenReturn(List.of(assignmentResponse()));

        mockMvc.perform(get("/api/instructor/courses/{courseId}/assignments", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(assignmentId.toString()));
    }

    @Test
    void getAssignmentDetail_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.getAssignmentDetail(courseId, assignmentId, instructorId))
                .thenReturn(AssignmentDetailResponse.builder().id(assignmentId).build());

        mockMvc.perform(get("/api/instructor/courses/{courseId}/assignments/{assignmentId}", courseId, assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()));
    }

    @Test
    void getAssignmentDetail_returns404() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.getAssignmentDetail(courseId, assignmentId, instructorId))
                .thenThrow(new AssignmentNotFoundException());

        mockMvc.perform(get("/api/instructor/courses/{courseId}/assignments/{assignmentId}", courseId, assignmentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAssignment_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.updateAssignment(any(), any(), any(), any())).thenReturn(assignmentResponse());

        var request = new UpdateAssignmentRequest();
        request.setTitle("Updated Title");

        mockMvc.perform(put("/api/instructor/courses/{courseId}/assignments/{assignmentId}", courseId, assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()));
    }

    @Test
    void deleteAssignment_returns204() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));

        mockMvc.perform(delete("/api/instructor/courses/{courseId}/assignments/{assignmentId}", courseId, assignmentId))
                .andExpect(status().isNoContent());
    }

    @Test
    void publishAssignment_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.publishAssignment(courseId, assignmentId, instructorId))
                .thenReturn(assignmentResponse());

        mockMvc.perform(put("/api/instructor/courses/{courseId}/assignments/{assignmentId}/publish", courseId, assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()));
    }

    @Test
    void closeAssignment_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.closeAssignment(courseId, assignmentId, instructorId))
                .thenReturn(assignmentResponse());

        mockMvc.perform(put("/api/instructor/courses/{courseId}/assignments/{assignmentId}/close", courseId, assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId.toString()));
    }

    @Test
    void uploadAttachment_returns201() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.uploadAttachment(any(), any(), any(), any()))
                .thenReturn(AssignmentAttachmentResponse.builder().id(UUID.randomUUID()).build());

        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[1024]);

        mockMvc.perform(multipart("/api/instructor/courses/{courseId}/assignments/{assignmentId}/attachments",
                        courseId, assignmentId)
                        .file(file))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteAttachment_returns204() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        UUID attachmentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/instructor/courses/{courseId}/assignments/{assignmentId}/attachments/{attachmentId}",
                        courseId, assignmentId, attachmentId))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/instructor/courses/{courseId}/assignments", courseId))
                .andExpect(status().isUnauthorized());
    }

    private AssignmentResponse assignmentResponse() {
        return AssignmentResponse.builder()
                .id(assignmentId)
                .courseId(courseId)
                .title("Test Assignment")
                .status(AssignmentStatus.DRAFT)
                .scope(AssignmentScope.ALL_GROUPS)
                .build();
    }
}
