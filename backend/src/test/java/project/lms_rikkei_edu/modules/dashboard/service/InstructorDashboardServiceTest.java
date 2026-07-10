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
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.dashboard.dto.response.CourseDistributionDto;
import project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.dto.response.PendingSubmissionDto;

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
class InstructorDashboardServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ResultSet rs;

    @InjectMocks
    private InstructorDashboardService instructorDashboardService;

    private UUID instructorId;

    @BeforeEach
    void setUp() {
        instructorId = UUID.randomUUID();
    }

    @Test
    void getDashboard_ShouldReturnFullDataAndCoverAllBranches() throws SQLException {
        // Mock scalar values
        when(jdbc.queryForObject(contains("status IN ('PUBLISHED', 'ACTIVE')"), eq(Integer.class), eq(instructorId)))
                .thenReturn(4);
        when(jdbc.queryForObject(contains("status = 'PENDING_APPROVAL'"), eq(Integer.class), eq(instructorId)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("COUNT(DISTINCT ce.student_id)"), eq(Integer.class), eq(instructorId)))
                .thenReturn(120);
        when(jdbc.queryForObject(contains("FROM study_groups WHERE instructor_id"), eq(Integer.class), eq(instructorId)))
                .thenReturn(5);
        when(jdbc.queryForObject(contains("status IN ('SUBMITTED', 'LATE')"), eq(Integer.class), eq(instructorId)))
                .thenReturn(15);
        when(jdbc.queryForObject(contains("COALESCE(AVG(cp.overall_percentage)"), eq(Double.class), eq(instructorId)))
                .thenReturn(85.54);

        // 7. Monthly completion rates RowCallbackHandler execution
        Calendar c = Calendar.getInstance();
        String currentYm = String.format("%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            when(rs.getString("ym")).thenReturn(currentYm, "1999-01");
            when(rs.getDouble("rate")).thenReturn(92.4, 10.0);
            handler.processRow(rs);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("cp.updated_at >= DATE_TRUNC('month'"), any(RowCallbackHandler.class), eq(instructorId));

        // 8. Course student distribution RowMapper execution
        when(jdbc.query(contains("COUNT(ce.student_id) AS student_count"), any(RowMapper.class), eq(instructorId)))
                .thenAnswer(invocation -> {
                    RowMapper<CourseDistributionDto> mapper = invocation.getArgument(1);
                    when(rs.getString("title")).thenReturn("Java Core");
                    when(rs.getInt("student_count")).thenReturn(50);
                    return Collections.singletonList(mapper.mapRow(rs, 0));
                });

        // 9. Recent submissions RowMapper execution
        UUID subId1 = UUID.randomUUID();
        UUID subId2 = UUID.randomUUID();
        when(jdbc.query(contains("s.status IN ('SUBMITTED', 'LATE')"), any(RowMapper.class), eq(instructorId)))
                .thenAnswer(invocation -> {
                    RowMapper<PendingSubmissionDto> mapper = invocation.getArgument(1);
                    when(rs.getString("id")).thenReturn(subId1.toString()).thenReturn(subId2.toString());
                    when(rs.getString("full_name")).thenReturn("Student A").thenReturn("Student B");
                    when(rs.getString("assignment_title")).thenReturn("HW 1").thenReturn("HW 2");
                    Timestamp nowTs = Timestamp.from(Instant.now());
                    when(rs.getTimestamp("submitted_at")).thenReturn(nowTs, nowTs, null, null);
                    when(rs.getString("status")).thenReturn("SUBMITTED").thenReturn("LATE");

                    List<PendingSubmissionDto> list = new ArrayList<>();
                    list.add(mapper.mapRow(rs, 0)); // non-null timestamp
                    list.add(mapper.mapRow(rs, 1)); // null timestamp fallback
                    return list;
                });

        // Act
        InstructorDashboardResponse response = instructorDashboardService.getDashboard(instructorId);

        // Assert
        assertNotNull(response);
        assertEquals(4, response.getActiveCoursesCount());
        assertEquals(1, response.getPendingCoursesCount());
        assertEquals(120, response.getTotalStudentsCount());
        assertEquals(5, response.getTotalGroupsCount());
        assertEquals(15, response.getPendingSubmissionsCount());
        assertEquals(85.5, response.getAverageCompletionRate());
        assertEquals(1, response.getCourseDistributions().size());
        assertEquals(2, response.getPendingSubmissions().size());
    }

    @Test
    void getDashboard_ShouldCoverNullBranchesAndFallbackToDefaults() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(null);
        when(jdbc.queryForObject(anyString(), eq(Double.class), any(Object[].class))).thenReturn(null);
        doNothing().when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.emptyList());

        InstructorDashboardResponse response = instructorDashboardService.getDashboard(instructorId);

        assertNotNull(response);
        assertEquals(0, response.getActiveCoursesCount());
        assertEquals(0, response.getPendingCoursesCount());
        assertEquals(0, response.getTotalStudentsCount());
        assertEquals(0, response.getTotalGroupsCount());
        assertEquals(0, response.getPendingSubmissionsCount());
        assertEquals(0.0, response.getAverageCompletionRate());
    }
}
