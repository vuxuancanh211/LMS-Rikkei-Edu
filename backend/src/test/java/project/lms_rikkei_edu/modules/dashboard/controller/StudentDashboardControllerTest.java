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

    @Test
    void getStats_ShouldReturnOk() {
        UUID studentId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardStatsResponse mockResponse = new project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardStatsResponse();
        when(studentDashboardService.getStats(studentId)).thenReturn(mockResponse);

        ResponseEntity<project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardStatsResponse> result = studentDashboardController.getStats();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getInProgressCourses_ShouldReturnOk() {
        UUID studentId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse.CourseSummaryDto> mockResponse = java.util.Collections.emptyList();
        when(studentDashboardService.getInProgressCourses(studentId)).thenReturn(mockResponse);

        ResponseEntity<java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse.CourseSummaryDto>> result = studentDashboardController.getInProgressCourses();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getDueAssignments_ShouldReturnOk() {
        UUID studentId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse.DueAssignmentDto> mockResponse = java.util.Collections.emptyList();
        when(studentDashboardService.getDueAssignments(studentId)).thenReturn(mockResponse);

        ResponseEntity<java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse.DueAssignmentDto>> result = studentDashboardController.getDueAssignments();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getWeeklyStudyHours_ShouldReturnOk() {
        UUID studentId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        java.util.List<Double> mockResponse = java.util.Collections.emptyList();
        when(studentDashboardService.getWeeklyStudyHours(studentId)).thenReturn(mockResponse);

        ResponseEntity<java.util.List<Double>> result = studentDashboardController.getWeeklyStudyHours();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void getSkillProgress_ShouldReturnOk() {
        UUID studentId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(studentId));
        java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse.SkillProgressDto> mockResponse = java.util.Collections.emptyList();
        when(studentDashboardService.getSkillProgress(studentId)).thenReturn(mockResponse);

        ResponseEntity<java.util.List<project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse.SkillProgressDto>> result = studentDashboardController.getSkillProgress();

        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(mockResponse, result.getBody());
    }
}
