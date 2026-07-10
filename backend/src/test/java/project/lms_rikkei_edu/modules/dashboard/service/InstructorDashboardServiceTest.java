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
import project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardResponse;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructorDashboardServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private InstructorDashboardService instructorDashboardService;

    private UUID instructorId;

    @BeforeEach
    void setUp() {
        instructorId = UUID.randomUUID();
    }

    @Test
    void getDashboard_ShouldReturnSuccessResponse() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(4);
        when(jdbc.queryForObject(anyString(), eq(Double.class), any(Object[].class)))
                .thenReturn(85.5);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());
        when(jdbc.query(anyString(), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        InstructorDashboardResponse response = instructorDashboardService.getDashboard(instructorId);

        assertNotNull(response);
    }
}
