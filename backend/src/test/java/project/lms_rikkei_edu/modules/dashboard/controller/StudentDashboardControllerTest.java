package project.lms_rikkei_edu.modules.dashboard.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.service.StudentDashboardService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentDashboardControllerTest {

    @Mock
    private StudentDashboardService studentDashboardService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private StudentDashboardController studentDashboardController;

    @Test
    void getDashboard_ShouldReturnOk() {
        UUID studentId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        StudentDashboardResponse mockResponse = new StudentDashboardResponse();
        when(studentDashboardService.getStudentDashboard(studentId)).thenReturn(mockResponse);

        ResponseEntity<StudentDashboardResponse> result = studentDashboardController.getDashboard();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }
}
