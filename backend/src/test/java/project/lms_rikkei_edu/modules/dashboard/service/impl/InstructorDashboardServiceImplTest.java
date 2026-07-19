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
import org.springframework.jdbc.core.ResultSetExtractor;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstructorDashboardServiceImplTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ResultSet rs;

    @InjectMocks
    private InstructorDashboardServiceImpl instructorDashboardService;

    private UUID instructorId;

    @BeforeEach
    void setUp() {
        instructorId = UUID.randomUUID();
    }

    @Test
    void getDashboard_ShouldReturnFullDataAndCoverAllBranches() throws SQLException {
        when(jdbc.query(contains("FROM courses"), any(ResultSetExtractor.class), eq(instructorId)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Map<String, Integer>> extractor = invocation.getArgument(1);
                    when(rs.next()).thenReturn(true);
                    when(rs.getInt("active_cnt")).thenReturn(4);
                    when(rs.getInt("pending_cnt")).thenReturn(1);
                    return extractor.extractData(rs);
                });
        when(jdbc.queryForObject(contains("COUNT(DISTINCT ce.student_id)"), eq(Integer.class), eq(instructorId)))
                .thenReturn(120);
        when(jdbc.queryForObject(contains("FROM study_groups WHERE instructor_id"), eq(Integer.class), eq(instructorId)))
                .thenReturn(5);
        when(jdbc.queryForObject(contains("status IN ('SUBMITTED', 'LATE')"), eq(Integer.class), eq(instructorId)))
                .thenReturn(15);
        when(jdbc.queryForObject(contains("COALESCE(AVG(cp.overall_percentage)"), eq(Double.class), eq(instructorId)))
                .thenReturn(85.54);

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

        when(jdbc.query(contains("COUNT(ce.student_id) AS student_count"), any(RowMapper.class), eq(instructorId)))
                .thenAnswer(invocation -> {
                    RowMapper<CourseDistributionDto> mapper = invocation.getArgument(1);
                    when(rs.getString("title")).thenReturn("Java Core");
                    when(rs.getInt("student_count")).thenReturn(50);
                    return Collections.singletonList(mapper.mapRow(rs, 0));
                });

        UUID subId1 = UUID.randomUUID();
        UUID subId2 = UUID.randomUUID();
        when(jdbc.query(contains("s.status IN ('SUBMITTED', 'LATE')"), any(RowMapper.class), eq(instructorId)))
                .thenAnswer(invocation -> {
                    RowMapper<PendingSubmissionDto> mapper = invocation.getArgument(1);
                    when(rs.getString("id")).thenReturn(subId1.toString()).thenReturn(subId2.toString());
                    when(rs.getString("full_name")).thenReturn("Student A").thenReturn("Student B");
                    when(rs.getString("assignment_title")).thenReturn("HW 1").thenReturn("HW 2");
                    when(rs.getString("group_name")).thenReturn("Group 1").thenReturn("Tự do");
                    Timestamp nowTs = Timestamp.from(Instant.now());
                    when(rs.getTimestamp("submitted_at")).thenReturn(nowTs, nowTs, null, null);
                    when(rs.getString("status")).thenReturn("SUBMITTED").thenReturn("LATE");

                    List<PendingSubmissionDto> list = new ArrayList<>();
                    list.add(mapper.mapRow(rs, 0));
                    list.add(mapper.mapRow(rs, 1));
                    return list;
                });

        InstructorDashboardResponse response = instructorDashboardService.getDashboard(instructorId);

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

    @Test
    void getStats_ShouldReturnStats() {
        when(jdbc.query(contains("FROM courses"), any(ResultSetExtractor.class), eq(instructorId)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Map<String, Integer>> extractor = invocation.getArgument(1);
                    when(rs.next()).thenReturn(true);
                    when(rs.getInt("active_cnt")).thenReturn(3);
                    when(rs.getInt("pending_cnt")).thenReturn(1);
                    return extractor.extractData(rs);
                });
        when(jdbc.queryForObject(contains("COUNT(DISTINCT ce.student_id)"), eq(Integer.class), eq(instructorId))).thenReturn(50);
        when(jdbc.queryForObject(contains("FROM study_groups WHERE instructor_id"), eq(Integer.class), eq(instructorId))).thenReturn(2);
        when(jdbc.queryForObject(contains("status IN ('SUBMITTED', 'LATE')"), eq(Integer.class), eq(instructorId))).thenReturn(7);
        when(jdbc.queryForObject(contains("COALESCE(AVG(cp.overall_percentage)"), eq(Double.class), eq(instructorId))).thenReturn(88.0);

        project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardStatsResponse res = instructorDashboardService.getStats(instructorId);
        assertNotNull(res);
        assertEquals(3, res.getActiveCoursesCount());
        assertEquals(1, res.getPendingCoursesCount());
        assertEquals(50, res.getTotalStudentsCount());
        assertEquals(2, res.getTotalGroupsCount());
        assertEquals(7, res.getPendingSubmissionsCount());
        assertEquals(88.0, res.getAverageCompletionRate());
    }

    @Test
    void getCompletionChart_ShouldReturnChartData() {
        doNothing().when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardChartResponse res = instructorDashboardService.getCompletionChart(instructorId);
        assertNotNull(res);
        assertNotNull(res.getMonthlyLabels());
        assertNotNull(res.getMonthlyCompletionRates());
    }

    @Test
    void getCourseDistributions_ShouldReturnDistributions() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.emptyList());
        when(jdbc.queryForObject(anyString(), eq(Double.class), any(Object[].class))).thenReturn(75.0);
        project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardDistributionsResponse res = instructorDashboardService.getCourseDistributions(instructorId);
        assertNotNull(res);
        assertNotNull(res.getCourseDistributions());
        assertEquals(75.0, res.getAverageCompletionRate());
    }

    @Test
    void getCourseDistributions_ShouldHandleNullRate() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.emptyList());
        when(jdbc.queryForObject(anyString(), eq(Double.class), any(Object[].class))).thenReturn(null);
        project.lms_rikkei_edu.modules.dashboard.dto.response.InstructorDashboardDistributionsResponse res = instructorDashboardService.getCourseDistributions(instructorId);
        assertNotNull(res);
        assertEquals(0.0, res.getAverageCompletionRate());
    }

    @Test
    void getPendingSubmissions_ShouldReturnSubmissions() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.emptyList());
        List<PendingSubmissionDto> res = instructorDashboardService.getPendingSubmissions(instructorId);
        assertNotNull(res);
    }
}
