package project.lms_rikkei_edu.modules.dashboard.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.service.InstructorDashboardService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstructorDashboardControllerTest {

    @Mock
    private InstructorDashboardService instructorDashboardService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private InstructorDashboardController instructorDashboardController;

    @Test
    void getDashboard_ShouldReturnOk() {
        UUID instructorId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        InstructorDashboardResponse mockResponse = new InstructorDashboardResponse();
        when(instructorDashboardService.getDashboard(instructorId)).thenReturn(mockResponse);

        ResponseEntity<InstructorDashboardResponse> result = instructorDashboardController.getDashboard();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }
}
