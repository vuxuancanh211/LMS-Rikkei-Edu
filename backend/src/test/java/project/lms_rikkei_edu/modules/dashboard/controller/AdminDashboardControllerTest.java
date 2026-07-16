package project.lms_rikkei_edu.modules.dashboard.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.service.AdminDashboardService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardService adminDashboardService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    @Test
    void getDashboard_ShouldReturnOk() {
        AdminDashboardResponse mockResponse = new AdminDashboardResponse();
        when(adminDashboardService.getDashboard()).thenReturn(mockResponse);

        ResponseEntity<AdminDashboardResponse> result = adminDashboardController.getDashboard();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getStats_ShouldReturnOk() {
        project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardStatsResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardStatsResponse();
        when(adminDashboardService.getStats()).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardStatsResponse> result = adminDashboardController.getStats();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getTrafficChart_ShouldReturnOk() {
        project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardTrafficResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardTrafficResponse();
        when(adminDashboardService.getTrafficChart()).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardTrafficResponse> result = adminDashboardController.getTrafficChart();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getCoursesChart_ShouldReturnOk() {
        project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardCoursesChartResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardCoursesChartResponse();
        when(adminDashboardService.getCoursesChart()).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardCoursesChartResponse> result = adminDashboardController.getCoursesChart();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getPendingApprovals_ShouldReturnOk() {
        java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.PendingApprovalDto> mockResponse = java.util.Collections.emptyList();
        when(adminDashboardService.getPendingApprovals()).thenReturn(mockResponse);

        ResponseEntity<java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.PendingApprovalDto>> result = adminDashboardController.getPendingApprovals();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getRecentActivities_ShouldReturnOk() {
        java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.SystemActivityDto> mockResponse = java.util.Collections.emptyList();
        when(adminDashboardService.getRecentActivities()).thenReturn(mockResponse);

        ResponseEntity<java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.SystemActivityDto>> result = adminDashboardController.getRecentActivities();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getUsersChart_ShouldReturnOk() {
        project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardUsersChartResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardUsersChartResponse();
        when(adminDashboardService.getUsersChart()).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardUsersChartResponse> result = adminDashboardController.getUsersChart();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getEnrollmentsChart_ShouldReturnOk() {
        project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardEnrollmentsChartResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardEnrollmentsChartResponse();
        when(adminDashboardService.getEnrollmentsChart()).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardEnrollmentsChartResponse> result = adminDashboardController.getEnrollmentsChart();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }
}
