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

    @Test
    void getStats_ShouldReturnOk() {
        UUID instructorId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardStatsResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardStatsResponse();
        when(instructorDashboardService.getStats(instructorId)).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardStatsResponse> result = instructorDashboardController.getStats();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getCompletionChart_ShouldReturnOk() {
        UUID instructorId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardChartResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardChartResponse();
        when(instructorDashboardService.getCompletionChart(instructorId)).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardChartResponse> result = instructorDashboardController.getCompletionChart();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getCourseDistributions_ShouldReturnOk() {
        UUID instructorId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardDistributionsResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardDistributionsResponse();
        when(instructorDashboardService.getCourseDistributions(instructorId)).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardDistributionsResponse> result = instructorDashboardController.getCourseDistributions();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getPendingSubmissions_ShouldReturnOk() {
        UUID instructorId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));
        java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.PendingSubmissionDto> mockResponse = java.util.Collections.emptyList();
        when(instructorDashboardService.getPendingSubmissions(instructorId)).thenReturn(mockResponse);

        ResponseEntity<java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.PendingSubmissionDto>> result = instructorDashboardController.getPendingSubmissions();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }
}
