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

import java.sql.ResultSet;
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
            assertThat(ctx.chapterProgress()).isEmpty();
            assertThat(ctx.assignmentScores()).isEmpty();
            assertThat(ctx.courseStructure()).isEmpty();
        }

        @Test
        void mapsCourseStructure_fromChapterAndLessonCounts() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("course_name")).thenReturn("DEVVVVVVVV");
            when(rs.getInt("chapter_count")).thenReturn(4);
            when(rs.getInt("lesson_count")).thenReturn(12);

            when(jdbc.query(argThat(sql -> sql.contains("lesson_count")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.courseStructure()).hasSize(1);
            var c = ctx.courseStructure().get(0);
            assertThat(c.courseName()).isEqualTo("DEVVVVVVVV");
            assertThat(c.chapterCount()).isEqualTo(4);
            assertThat(c.lessonCount()).isEqualTo(12);
        }

        @Test
        void mapsChapterProgress_fromChaptersQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("course_name")).thenReturn("Spring Boot Microservices");
            when(rs.getString("chapter_title")).thenReturn("Chương 1: Giới thiệu");
            when(rs.getInt("completed_lessons")).thenReturn(3);
            when(rs.getInt("total_lessons")).thenReturn(5);

            when(jdbc.query(argThat(sql -> sql.contains("chapters ch")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.chapterProgress()).hasSize(1);
            var p = ctx.chapterProgress().get(0);
            assertThat(p.courseName()).isEqualTo("Spring Boot Microservices");
            assertThat(p.chapterTitle()).isEqualTo("Chương 1: Giới thiệu");
            assertThat(p.completedLessons()).isEqualTo(3);
            assertThat(p.totalLessons()).isEqualTo(5);
        }

        @Test
        void mapsAssignmentScores_fromGradedSubmissionsQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("title")).thenReturn("Bài tập 1: REST API");
            when(rs.getString("course_name")).thenReturn("Spring Boot Microservices");
            when(rs.getDouble("score")).thenReturn(92.0);
            when(rs.getDouble("max_score")).thenReturn(100.0);
            when(rs.getString("graded_at")).thenReturn("15/06/2026 10:00");

            when(jdbc.query(argThat(sql -> sql.contains("graded_at")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.assignmentScores()).hasSize(1);
            var s = ctx.assignmentScores().get(0);
            assertThat(s.assignmentTitle()).isEqualTo("Bài tập 1: REST API");
            assertThat(s.score()).isEqualTo(92.0);
            assertThat(s.maxScore()).isEqualTo(100.0);
        }

        @Test
        void mapsCourses_fromEnrollmentsQuery() throws Exception {
            UUID courseId = UUID.randomUUID();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(courseId);
            when(rs.getString("title")).thenReturn("ReactJS Nâng cao");
            when(rs.getString("progress_status")).thenReturn("IN_PROGRESS");
            when(rs.getDouble("progress_pct")).thenReturn(45.0);

            when(jdbc.query(argThat(sql -> sql.contains("NOT_STARTED")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.courses()).hasSize(1);
            var c = ctx.courses().get(0);
            assertThat(c.courseId()).isEqualTo(courseId);
            assertThat(c.title()).isEqualTo("ReactJS Nâng cao");
            assertThat(c.progressPct()).isEqualTo(45.0);
        }

        @Test
        void mapsDeadlines_fromAssignmentsQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("title")).thenReturn("Bài tập 5");
            when(rs.getString("course_name")).thenReturn("ReactJS Nâng cao");
            when(rs.getString("deadline")).thenReturn("10/07/2026 23:59");
            when(rs.getBoolean("is_late")).thenReturn(true);

            when(jdbc.query(argThat(sql -> sql.contains("NOW() - INTERVAL '1 day'")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.upcomingDeadlines()).hasSize(1);
            var d = ctx.upcomingDeadlines().get(0);
            assertThat(d.assignmentTitle()).isEqualTo("Bài tập 5");
            assertThat(d.isLate()).isTrue();
        }

        @Test
        void mapsGroups_fromGroupMembersQuery() throws Exception {
            UUID groupId = UUID.randomUUID();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(groupId);
            when(rs.getString("name")).thenReturn("Nhóm A1");
            when(rs.getString("course_name")).thenReturn("ReactJS Nâng cao");

            when(jdbc.query(argThat(sql -> sql.contains("gm.joined_at DESC")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.groups()).hasSize(1);
            assertThat(ctx.groups().get(0).groupId()).isEqualTo(groupId);
            assertThat(ctx.groups().get(0).groupName()).isEqualTo("Nhóm A1");
        }

        @Test
        void mapsRecentLessons_fromLessonProgressQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("lesson_title")).thenReturn("Bài 1: Hooks");
            when(rs.getString("chapter_title")).thenReturn("Chương 1");
            when(rs.getString("course_name")).thenReturn("ReactJS Nâng cao");
            when(rs.getString("status")).thenReturn("COMPLETED");
            when(rs.getString("last_accessed_at")).thenReturn("01/07/2026 10:00");

            when(jdbc.query(argThat(sql -> sql.contains("lesson_progress lp")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.recentLessons()).hasSize(1);
            assertThat(ctx.recentLessons().get(0).lessonTitle()).isEqualTo("Bài 1: Hooks");
            assertThat(ctx.recentLessons().get(0).status()).isEqualTo("COMPLETED");
        }

        @Test
        void mapsRecentQuizResults_fromQuizAttemptsQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("quiz_title")).thenReturn("Quiz Chương 1");
            when(rs.getString("course_name")).thenReturn("ReactJS Nâng cao");
            when(rs.getDouble("score")).thenReturn(8.0);
            when(rs.getInt("total_questions")).thenReturn(10);
            when(rs.getBoolean("is_passed")).thenReturn(true);
            when(rs.getString("submitted_at")).thenReturn("01/07/2026 11:00");

            when(jdbc.query(argThat(sql -> sql.contains("qa.submitted_at IS NOT NULL")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.recentQuizResults()).hasSize(1);
            var q = ctx.recentQuizResults().get(0);
            assertThat(q.quizTitle()).isEqualTo("Quiz Chương 1");
            assertThat(q.totalQuestions()).isEqualTo(10);
            assertThat(q.isPassed()).isTrue();
        }

        @Test
        void mapsUnsubmittedAssignments_fromNotExistsQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("title")).thenReturn("Bài tập 6");
            when(rs.getString("course_name")).thenReturn("ReactJS Nâng cao");
            when(rs.getString("deadline")).thenReturn("20/07/2026 23:59");
            when(rs.getBoolean("is_overdue")).thenReturn(false);

            when(jdbc.query(argThat(sql -> sql.contains("NOT EXISTS")), any(RowMapper.class), any(), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(studentId);

            assertThat(ctx.unsubmittedAssignments()).hasSize(1);
            var a = ctx.unsubmittedAssignments().get(0);
            assertThat(a.assignmentTitle()).isEqualTo("Bài tập 6");
            assertThat(a.isOverdue()).isFalse();
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
            assertThat(ctx.quizStats()).isEmpty();
            assertThat(ctx.topStudents()).isEmpty();
            assertThat(ctx.courseApprovals()).isEmpty();
            assertThat(ctx.courseStructure()).isEmpty();
        }

        @Test
        void mapsCourseStructure_fromChapterAndLessonCounts() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("course_name")).thenReturn("DEVVVVVVVV");
            when(rs.getInt("chapter_count")).thenReturn(4);
            when(rs.getInt("lesson_count")).thenReturn(12);

            when(jdbc.query(argThat(sql -> sql.contains("lesson_count")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.courseStructure()).hasSize(1);
            var c = ctx.courseStructure().get(0);
            assertThat(c.courseName()).isEqualTo("DEVVVVVVVV");
            assertThat(c.chapterCount()).isEqualTo(4);
            assertThat(c.lessonCount()).isEqualTo(12);
        }

        @Test
        void mapsQuizStats_fromQuizAttemptsQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("quiz_title")).thenReturn("Quiz Chương 1");
            when(rs.getString("course_name")).thenReturn("Spring Boot Microservices");
            when(rs.getDouble("avg_score")).thenReturn(65.5);
            when(rs.getDouble("pass_rate_percent")).thenReturn(50.0);
            when(rs.getInt("total_attempts")).thenReturn(8);

            when(jdbc.query(argThat(sql -> sql.contains("pass_rate_percent")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.quizStats()).hasSize(1);
            var q = ctx.quizStats().get(0);
            assertThat(q.quizTitle()).isEqualTo("Quiz Chương 1");
            assertThat(q.avgScore()).isEqualTo(65.5);
            assertThat(q.passRatePercent()).isEqualTo(50.0);
            assertThat(q.totalAttempts()).isEqualTo(8);
        }

        @Test
        void mapsTopStudents_fromHighProgressQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("student_name")).thenReturn("Phạm Quốc Hùng");
            when(rs.getString("course_name")).thenReturn("Spring Boot Microservices");
            when(rs.getDouble("progress_pct")).thenReturn(95.0);

            when(jdbc.query(argThat(sql -> sql.contains(">= 80")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.topStudents()).hasSize(1);
            assertThat(ctx.topStudents().get(0).studentName()).isEqualTo("Phạm Quốc Hùng");
            assertThat(ctx.topStudents().get(0).progressPct()).isEqualTo(95.0);
        }

        @Test
        void mapsCourseApprovals_fromCoursesStatusQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("title")).thenReturn("Docker & Kubernetes Cơ Bản");
            when(rs.getString("status")).thenReturn("REJECTED");
            when(rs.getString("rejection_reason")).thenReturn("Thiếu nội dung thực hành");

            when(jdbc.query(argThat(sql -> sql.contains("rejection_reason")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.courseApprovals()).hasSize(1);
            var c = ctx.courseApprovals().get(0);
            assertThat(c.courseName()).isEqualTo("Docker & Kubernetes Cơ Bản");
            assertThat(c.status()).isEqualTo("REJECTED");
            assertThat(c.rejectionReason()).isEqualTo("Thiếu nội dung thực hành");
        }

        @Test
        void mapsCourses_fromOwnedCoursesQuery() throws Exception {
            UUID courseId = UUID.randomUUID();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(courseId);
            when(rs.getString("title")).thenReturn("Spring Boot Microservices");
            when(rs.getString("progress_status")).thenReturn("PUBLISHED");
            when(rs.getDouble("enrollment_count")).thenReturn(120.0);

            when(jdbc.query(argThat(sql -> sql.contains("enrollment_count")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.courses()).hasSize(1);
            var c = ctx.courses().get(0);
            assertThat(c.courseId()).isEqualTo(courseId);
            assertThat(c.progressPct()).isEqualTo(120.0);
        }

        @Test
        void mapsDeadlines_fromInstructorAssignmentsQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("title")).thenReturn("Bài tập chấm gấp");
            when(rs.getString("course_name")).thenReturn("Spring Boot Microservices");
            when(rs.getString("deadline")).thenReturn("12/07/2026 23:59");
            when(rs.getBoolean("is_late")).thenReturn(false);

            when(jdbc.query(argThat(sql -> sql.contains("NOW() - INTERVAL '3 days'")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.upcomingDeadlines()).hasSize(1);
            assertThat(ctx.upcomingDeadlines().get(0).assignmentTitle()).isEqualTo("Bài tập chấm gấp");
        }

        @Test
        void mapsGroups_fromManagedGroupsQuery() throws Exception {
            UUID groupId = UUID.randomUUID();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(groupId);
            when(rs.getString("name")).thenReturn("Nhóm B1");
            when(rs.getString("course_name")).thenReturn("Spring Boot Microservices");

            when(jdbc.query(argThat(sql -> sql.contains("sg.created_at DESC")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.groups()).hasSize(1);
            assertThat(ctx.groups().get(0).groupName()).isEqualTo("Nhóm B1");
        }

        @Test
        void mapsSubmissionGaps_fromAssignmentsSubmissionQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("assignment_title")).thenReturn("Bài tập 1");
            when(rs.getString("course_name")).thenReturn("Spring Boot Microservices");
            when(rs.getString("deadline")).thenReturn("15/07/2026 23:59");
            when(rs.getInt("total_enrolled")).thenReturn(30);
            when(rs.getInt("submitted")).thenReturn(20);

            when(jdbc.query(argThat(sql -> sql.contains("total_enrolled")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.submissionGaps()).hasSize(1);
            var s = ctx.submissionGaps().get(0);
            assertThat(s.totalEnrolled()).isEqualTo(30);
            assertThat(s.submitted()).isEqualTo(20);
            assertThat(s.notSubmitted()).isEqualTo(10);
        }

        @Test
        void mapsAtRiskStudents_fromLowProgressQuery() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("student_name")).thenReturn("Lê Văn C");
            when(rs.getString("course_name")).thenReturn("Spring Boot Microservices");
            when(rs.getDouble("progress_pct")).thenReturn(10.0);
            when(rs.getInt("days_enrolled")).thenReturn(15);

            when(jdbc.query(argThat(sql -> sql.contains("days_enrolled")), any(RowMapper.class), any()))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0)));

            UserContext ctx = service.load(instructorId);

            assertThat(ctx.atRiskStudents()).hasSize(1);
            var a = ctx.atRiskStudents().get(0);
            assertThat(a.studentName()).isEqualTo("Lê Văn C");
            assertThat(a.daysEnrolled()).isEqualTo(15);
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

        @Test
        void toLong_coercesNonLongNumberTypes() {
            // Some JDBC drivers may return plain Integer for COUNT(*) instead of Long.
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("total_users", 42);
            stats.put("total_students", 30);
            stats.put("total_instructors", 12);
            stats.put("total_courses", 8);
            stats.put("published_courses", 5);
            stats.put("total_enrollments", 200L);
            stats.put("active_conversations", null);
            when(jdbc.queryForMap(anyString())).thenReturn(stats);

            UserContext ctx = service.load(adminId);

            assertThat(ctx.adminStats().totalUsers()).isEqualTo(42L);
            assertThat(ctx.adminStats().totalEnrollments()).isEqualTo(200L);
            assertThat(ctx.adminStats().activeConversations()).isZero();
        }
    }
}
