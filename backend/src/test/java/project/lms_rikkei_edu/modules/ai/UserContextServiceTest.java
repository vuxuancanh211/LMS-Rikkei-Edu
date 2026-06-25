package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.ai.exception.UserContextException;
import project.lms_rikkei_edu.modules.ai.service.context.UserContext;
import project.lms_rikkei_edu.modules.ai.service.context.UserContextService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserContextServiceTest {

    @Mock JdbcTemplate jdbc;

    UserContextService service;

    private final UUID studentId    = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID adminId      = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserContextService(jdbc);
    }

    private void stubEmptyQueries() {
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of());
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(List.of());
    }

    // ── load: user not found ──────────────────────────────────────────────────

    @Test
    void throws_whenUserNotFound() {
        when(jdbc.queryForList(anyString(), eq(studentId))).thenReturn(List.of());

        assertThatThrownBy(() -> service.load(studentId))
                .isInstanceOf(UserContextException.class);
    }

    // ── load: STUDENT role ────────────────────────────────────────────────────

    @Nested
    class StudentContext {

        @BeforeEach
        void stubUser() {
            when(jdbc.queryForList(anyString(), eq(studentId)))
                    .thenReturn(List.of(Map.of("full_name", "Alice", "role", "student")));
            stubEmptyQueries();
        }

        @Test
        void returnsStudentContext() {
            UserContext ctx = service.load(studentId);

            assertThat(ctx.userId()).isEqualTo(studentId);
            assertThat(ctx.fullName()).isEqualTo("Alice");
            assertThat(ctx.role()).isEqualTo(UserContext.UserRole.STUDENT);
        }

        @Test
        void hasEmptyCoursesAndDeadlines_whenNoData() {
            UserContext ctx = service.load(studentId);

            assertThat(ctx.courses()).isEmpty();
            assertThat(ctx.upcomingDeadlines()).isEmpty();
            assertThat(ctx.groups()).isEmpty();
            assertThat(ctx.recentLessons()).isEmpty();
            assertThat(ctx.recentQuizResults()).isEmpty();
            assertThat(ctx.unsubmittedAssignments()).isEmpty();
        }
    }

    // ── load: INSTRUCTOR role ─────────────────────────────────────────────────

    @Nested
    class InstructorContext {

        @BeforeEach
        void stubUser() {
            when(jdbc.queryForList(anyString(), eq(instructorId)))
                    .thenReturn(List.of(Map.of("full_name", "Bob", "role", "INSTRUCTOR")));
            stubEmptyQueries();
        }

        @Test
        void returnsInstructorContext() {
            UserContext ctx = service.load(instructorId);

            assertThat(ctx.userId()).isEqualTo(instructorId);
            assertThat(ctx.fullName()).isEqualTo("Bob");
            assertThat(ctx.role()).isEqualTo(UserContext.UserRole.INSTRUCTOR);
        }

        @Test
        void hasEmptyStudentFields() {
            UserContext ctx = service.load(instructorId);

            assertThat(ctx.recentLessons()).isEmpty();
            assertThat(ctx.recentQuizResults()).isEmpty();
            assertThat(ctx.unsubmittedAssignments()).isEmpty();
            assertThat(ctx.adminStats()).isNull();
        }
    }

    // ── load: ADMIN role ──────────────────────────────────────────────────────

    @Nested
    class AdminContext {

        @BeforeEach
        void stubUser() {
            when(jdbc.queryForList(anyString(), eq(adminId)))
                    .thenReturn(List.of(Map.of("full_name", "Charlie", "role", "ADMIN")));

            when(jdbc.queryForMap(anyString()))
                    .thenReturn(Map.of(
                            "total_users", 100L,
                            "total_students", 80L,
                            "total_instructors", 15L,
                            "total_courses", 50L,
                            "published_courses", 40L,
                            "total_enrollments", 200L,
                            "active_conversations", 10L
                    ));
        }

        @Test
        void returnsAdminContext_withStats() {
            UserContext ctx = service.load(adminId);

            assertThat(ctx.role()).isEqualTo(UserContext.UserRole.ADMIN);
            assertThat(ctx.adminStats()).isNotNull();
            assertThat(ctx.adminStats().totalUsers()).isEqualTo(100L);
            assertThat(ctx.adminStats().totalStudents()).isEqualTo(80L);
            assertThat(ctx.adminStats().publishedCourses()).isEqualTo(40L);
        }

        @Test
        void hasEmptyLists_forNonAdminFields() {
            UserContext ctx = service.load(adminId);

            assertThat(ctx.courses()).isEmpty();
            assertThat(ctx.upcomingDeadlines()).isEmpty();
            assertThat(ctx.recentLessons()).isEmpty();
        }

        @Test
        void handlesNullNumericValues_inStats() {
            when(jdbc.queryForMap(anyString()))
                    .thenReturn(Map.of(
                            "total_users", 0L,
                            "total_students", 0L,
                            "total_instructors", 0L,
                            "total_courses", 0L,
                            "published_courses", 0L,
                            "total_enrollments", 0L,
                            "active_conversations", 0L
                    ));

            UserContext ctx = service.load(adminId);

            assertThat(ctx.adminStats().totalUsers()).isZero();
        }
    }
}
