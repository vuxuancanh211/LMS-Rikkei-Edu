package project.lms_rikkei_edu.modules.assignment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.assignment.dto.response.AssignmentResponse;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentScope;
import project.lms_rikkei_edu.modules.assignment.enums.AssignmentStatus;
import project.lms_rikkei_edu.modules.assignment.service.AssignmentService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstructorAssignmentQueryControllerTest {

    private AssignmentService assignmentService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    private final UUID instructorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assignmentService = mock(AssignmentService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InstructorAssignmentQueryController(assignmentService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllAssignments_returns200() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        when(assignmentService.getAllAssignments(instructorId)).thenReturn(List.of(
                AssignmentResponse.builder()
                        .id(UUID.randomUUID())
                        .courseId(courseId)
                        .title("Assignment 1")
                        .status(AssignmentStatus.DRAFT)
                        .scope(AssignmentScope.ALL_GROUPS)
                        .build(),
                AssignmentResponse.builder()
                        .id(UUID.randomUUID())
                        .courseId(courseId)
                        .title("Assignment 2")
                        .status(AssignmentStatus.PUBLISHED)
                        .scope(AssignmentScope.SPECIFIC_GROUPS)
                        .build()
        ));

        mockMvc.perform(get("/api/instructor/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Assignment 1"))
                .andExpect(jsonPath("$[1].title").value("Assignment 2"));
    }

    @Test
    void getAllAssignments_unauthorized_returns401() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/instructor/assignments"))
                .andExpect(status().isUnauthorized());
    }
}
