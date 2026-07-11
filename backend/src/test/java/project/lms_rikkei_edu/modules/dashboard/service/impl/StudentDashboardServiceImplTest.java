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
import project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentDashboardServiceImplTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ResultSet rs;

    @InjectMocks
    private StudentDashboardServiceImpl studentDashboardService;

    private UUID studentId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
    }

    @Test
    void getStudentDashboard_ShouldReturnFullDataAndCoverAllBranches() throws SQLException {
        when(jdbc.queryForObject(contains("SELECT COALESCE(full_name"), eq(String.class), eq(studentId)))
                .thenReturn("Nguyễn Văn Student");
        when(jdbc.queryForObject(contains("FROM course_enrollments"), eq(Integer.class), eq(studentId)))
                .thenReturn(3);
        when(jdbc.queryForObject(contains("overall_percentage >= 70"), eq(Integer.class), eq(studentId)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("FROM certificates"), eq(Integer.class), eq(studentId)))
                .thenReturn(2);

        when(jdbc.query(contains("FROM assignments a"), any(RowMapper.class), eq(studentId), eq(studentId)))
                .thenAnswer(invocation -> {
                    RowMapper<StudentDashboardResponse.DueAssignmentDto> mapper = invocation.getArgument(1);
                    when(rs.getString("title")).thenReturn("Quiz 1").thenReturn("Exercise 2");
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("deadline_str")).thenReturn("15/07/2026").thenReturn(null);
                    when(rs.getString("status")).thenReturn("pending").thenReturn("late");
                    
                    List<StudentDashboardResponse.DueAssignmentDto> list = new ArrayList<>();
                    list.add(mapper.mapRow(rs, 0));
                    list.add(mapper.mapRow(rs, 1));
                    return list;
                });

        when(jdbc.query(contains("FROM course_enrollments ce"), any(RowMapper.class), eq(studentId)))
                .thenAnswer(invocation -> {
                    RowMapper<StudentDashboardResponse.CourseSummaryDto> mapper = invocation.getArgument(1);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("title")).thenReturn("React Masterclass");
                    when(rs.getString("category_name")).thenReturn("Lập trình Web").thenReturn(null);
                    when(rs.getString("thumbnail_url")).thenReturn("http://img.png").thenReturn(null);
                    when(rs.getInt("progress")).thenReturn(65);

                    List<StudentDashboardResponse.CourseSummaryDto> list = new ArrayList<>();
                    list.add(mapper.mapRow(rs, 0));
                    list.add(mapper.mapRow(rs, 1));
                    return list;
                });

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            when(rs.getInt("dow")).thenReturn(1, 7, 0);
            when(rs.getDouble("hours")).thenReturn(2.35, 1.1, 0.0);
            handler.processRow(rs);
            handler.processRow(rs);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("FROM lesson_progress"), any(RowCallbackHandler.class), eq(studentId));

        when(jdbc.query(contains("FROM course_categories cat"), any(RowMapper.class), eq(studentId)))
                .thenAnswer(invocation -> {
                    RowMapper<StudentDashboardResponse.SkillProgressDto> mapper = invocation.getArgument(1);
                    when(rs.getString("title")).thenReturn("Frontend");
                    when(rs.getDouble("progress")).thenReturn(88.4);
                    return Collections.singletonList(mapper.mapRow(rs, 0));
                });

        StudentDashboardResponse response = studentDashboardService.getStudentDashboard(studentId);

        assertNotNull(response);
        assertEquals("Nguyễn Văn Student", response.getStudentName());
        assertEquals(2, response.getStats().getPendingTasksCount());
        assertEquals(1, response.getStats().getDueSoonTasksCount());
        assertNotNull(response.getStats().getWeeklyHoursTrend());
        assertEquals(2, response.getInProgressCourses().size());
        assertEquals("Khóa học", response.getInProgressCourses().get(1).getCategory());
        assertEquals("assets/courses/placeholder.png", response.getInProgressCourses().get(1).getThumbnailUrl());
    }

    @Test
    void getStudentDashboard_ShouldCoverNullBranchesAndFallbackToDefaults() {
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class))).thenReturn(null);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(null);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.emptyList());
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        StudentDashboardResponse response = studentDashboardService.getStudentDashboard(studentId);

        assertNotNull(response);
        assertEquals("Học viên", response.getStudentName());
        assertEquals(0, response.getStats().getActiveCoursesCount());
        assertEquals(0, response.getStats().getNearCompletionCoursesCount());
        assertEquals(0, response.getStats().getCertificatesCount());
        assertNull(response.getStats().getWeeklyHoursTrend());
    }
}
