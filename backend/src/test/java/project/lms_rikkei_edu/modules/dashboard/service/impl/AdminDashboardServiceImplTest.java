package project.lms_rikkei_edu.modules.dashboard.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.dto.response.PendingApprovalDto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDashboardServiceImplTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ResultSet rs;

    @InjectMocks
    private AdminDashboardServiceImpl adminDashboardService;

    @Test
    void getDashboard_ShouldReturnFullDataAndCoverAllBranches() throws SQLException {
        // Mock simple scalar counts
        when(jdbc.queryForObject(contains("role = 'STUDENT'"), eq(Integer.class))).thenReturn(500);
        when(jdbc.queryForObject(contains("role = 'INSTRUCTOR'"), eq(Integer.class))).thenReturn(45);
        when(jdbc.queryForObject(contains("status IN ('PUBLISHED', 'APPROVED')"), eq(Integer.class))).thenReturn(120);
        when(jdbc.queryForObject(contains("AVG(overall_percentage)"), eq(Double.class))).thenReturn(79.84);

        // Prepare current YM & Date strings
        Calendar c = Calendar.getInstance();
        String currentYm = String.format("%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
        String currentDateStr = String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));

        // 5a. Monthly Traffic RowCallbackHandler
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            when(rs.getString("ym")).thenReturn(currentYm, "1999-01");
            when(rs.getDouble("cnt")).thenReturn(350.0, 10.0);
            handler.processRow(rs);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("FROM lesson_progress\n                WHERE COALESCE(last_accessed_at"), any(RowCallbackHandler.class));

        // 5a2. Monthly New Courses RowCallbackHandler
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            when(rs.getString("ym")).thenReturn(currentYm, "1999-01");
            when(rs.getInt("cnt")).thenReturn(15, 2);
            handler.processRow(rs);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("FROM courses\n                WHERE created_at >= DATE_TRUNC('month'"), any(RowCallbackHandler.class));

        // 5b. Weekly Traffic RowCallbackHandler
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            when(rs.getString("dstr")).thenReturn(currentDateStr, "1999-01-01");
            when(rs.getDouble("cnt")).thenReturn(120.0, 5.0);
            handler.processRow(rs);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("FROM lesson_progress\n                WHERE COALESCE(last_accessed_at, first_accessed_at) >= NOW() - INTERVAL '7 days'"), any(RowCallbackHandler.class));

        // 5b2. Weekly New Courses RowCallbackHandler
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            when(rs.getString("dstr")).thenReturn(currentDateStr, "1999-01-01");
            when(rs.getInt("cnt")).thenReturn(3, 0);
            handler.processRow(rs);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("FROM courses\n                WHERE created_at >= NOW() - INTERVAL '7 days'"), any(RowCallbackHandler.class));

        // 6. Pending approvals list RowMapper
        UUID courseId = UUID.randomUUID();
        when(jdbc.query(contains("status IN ('PENDING', 'PENDING_UPDATE')"), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<PendingApprovalDto> mapper = invocation.getArgument(1);
                    when(rs.getString("id")).thenReturn(courseId.toString());
                    when(rs.getString("course_title")).thenReturn("Spring Master");
                    when(rs.getString("instructor_name")).thenReturn("Instructor X");
                    Timestamp nowTs = Timestamp.from(Instant.now());
                    when(rs.getTimestamp("created_at")).thenReturn(nowTs, nowTs, null, null);
                    when(rs.getString("status")).thenReturn("PENDING").thenReturn("PENDING_UPDATE");

                    List<PendingApprovalDto> list = new ArrayList<>();
                    list.add(mapper.mapRow(rs, 0));
                    list.add(mapper.mapRow(rs, 1));
                    return list;
                });

        // 7a. System activities (from courses) RowCallbackHandler
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            Timestamp nowTs = Timestamp.from(Instant.now());
            when(rs.getString("id")).thenReturn(UUID.randomUUID().toString());
            when(rs.getString("full_name")).thenReturn("Admin Who");
            when(rs.getString("title")).thenReturn("React 19");
            when(rs.getTimestamp("act_time")).thenReturn(nowTs, nowTs, null, null, nowTs, nowTs);
            when(rs.getString("status")).thenReturn("PENDING", "PUBLISHED", "UPDATED");
            
            handler.processRow(rs); // PENDING branch
            handler.processRow(rs); // PUBLISHED + null time branch
            handler.processRow(rs); // UPDATED branch
            return null;
        }).when(jdbc).query(contains("FROM courses c JOIN users u ON u.id = c.instructor_id"), any(RowCallbackHandler.class));

        // 7b. System activities (from assignment submissions) RowCallbackHandler
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            Timestamp nowTs = Timestamp.from(Instant.now());
            when(rs.getString("id")).thenReturn(UUID.randomUUID().toString());
            when(rs.getString("full_name")).thenReturn("Student Who");
            when(rs.getString("title")).thenReturn("Assignment 1");
            when(rs.getTimestamp("submitted_at")).thenReturn(nowTs, nowTs, null, null);
            
            handler.processRow(rs);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("FROM assignment_submissions s\n                JOIN assignments a"), any(RowCallbackHandler.class));

        // Act
        AdminDashboardResponse response = adminDashboardService.getDashboard();

        // Assert
        assertNotNull(response);
        assertEquals(500, response.getTotalStudentsCount());
        assertEquals(45, response.getTotalInstructorsCount());
        assertEquals(120, response.getActiveCoursesCount());
        assertEquals(79.8, response.getAverageCompletionRate());
        assertEquals(2, response.getPendingApprovals().size());
        assertTrue(response.getRecentActivities().size() <= 6);
    }

    @Test
    void getDashboard_ShouldCoverNullBranchesAndFallbackToDefaults() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);
        when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(null);
        doNothing().when(jdbc).query(anyString(), any(RowCallbackHandler.class));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        AdminDashboardResponse response = adminDashboardService.getDashboard();

        assertNotNull(response);
        assertEquals(0, response.getTotalStudentsCount());
        assertEquals(0, response.getTotalInstructorsCount());
        assertEquals(0, response.getActiveCoursesCount());
        assertEquals(0.0, response.getAverageCompletionRate());
    }
}
