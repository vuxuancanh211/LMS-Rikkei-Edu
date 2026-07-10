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
}
