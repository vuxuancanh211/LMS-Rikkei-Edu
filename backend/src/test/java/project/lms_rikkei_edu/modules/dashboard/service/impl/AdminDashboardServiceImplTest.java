package project.lms_rikkei_edu.modules.dashboard.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardResponse;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDashboardServiceImplTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private AdminDashboardServiceImpl adminDashboardService;

    @Test
    void getDashboard_ShouldReturnSuccessResponse() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(100);
        when(jdbc.queryForObject(anyString(), eq(Double.class)))
                .thenReturn(78.2);
        when(jdbc.query(anyString(), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        AdminDashboardResponse response = adminDashboardService.getDashboard();

        assertNotNull(response);
    }
}
