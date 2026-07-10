package project.lms_rikkei_edu.modules.dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentDashboardServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private StudentDashboardService studentDashboardService;

    private UUID studentId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
    }

    @Test
    void getStudentDashboard_ShouldReturnSuccessResponse() {
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("Nguyễn Văn Student");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(3);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());
        when(jdbc.query(anyString(), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        StudentDashboardResponse response = studentDashboardService.getStudentDashboard(studentId);

        assertNotNull(response);
        assertNotNull(response.getStats());
    }
}
