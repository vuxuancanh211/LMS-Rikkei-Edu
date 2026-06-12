package project.lms_rikkei_edu.modules.ai.service.context;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.ai.exception.UserContextException;
import project.lms_rikkei_edu.modules.ai.service.context.UserContext.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserContextService {

    private final JdbcTemplate jdbc;

    public UserContext load(UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT full_name, role FROM users WHERE id = ?", userId);

        if (rows.isEmpty()) {
            throw new UserContextException(userId);
        }

        Map<String, Object> row = rows.get(0);
        String fullName = (String) row.get("full_name");
        UserRole role = UserRole.valueOf(((String) row.get("role")).toUpperCase());

        return switch (role) {
            case STUDENT    -> buildStudentContext(userId, fullName);
            case INSTRUCTOR -> buildInstructorContext(userId, fullName);
            case ADMIN      -> buildAdminContext(userId, fullName);
        };
    }

    // ── STUDENT ──────────────────────────────────────────────────────────────

    private UserContext buildStudentContext(UUID studentId, String fullName) {
        List<CourseInfo> courses = jdbc.query("""
                SELECT c.id, c.title,
                       COALESCE(cp.status, 'NOT_STARTED') AS progress_status,
                       COALESCE(cp.overall_percentage, 0)  AS progress_pct
                FROM course_enrollments ce
                JOIN courses c ON c.id = ce.course_id
                LEFT JOIN course_progress cp ON cp.student_id = ce.student_id
                                             AND cp.course_id = ce.course_id
                WHERE ce.student_id = ?
                ORDER BY ce.enrolled_at DESC
                """,
                (rs, i) -> new CourseInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("progress_status"),
                        rs.getDouble("progress_pct")),
                studentId);

        List<DeadlineInfo> deadlines = jdbc.query("""
                SELECT a.title, c.title AS course_name,
                       TO_CHAR(a.deadline, 'DD/MM/YYYY HH24:MI') AS deadline,
                       (a.deadline < NOW()) AS is_late
                FROM assignments a
                JOIN courses c ON c.id = a.course_id
                JOIN course_enrollments ce ON ce.course_id = a.course_id
                                          AND ce.student_id = ?
                WHERE a.status = 'PUBLISHED'
                  AND a.deadline BETWEEN NOW() - INTERVAL '1 day' AND NOW() + INTERVAL '7 days'
                ORDER BY a.deadline ASC
                """,
                (rs, i) -> new DeadlineInfo(
                        rs.getString("title"),
                        rs.getString("course_name"),
                        rs.getString("deadline"),
                        rs.getBoolean("is_late")),
                studentId);

        List<GroupInfo> groups = jdbc.query("""
                SELECT sg.id, sg.name, c.title AS course_name
                FROM group_members gm
                JOIN study_groups sg ON sg.id = gm.group_id
                JOIN courses c ON c.id = sg.course_id
                WHERE gm.student_id = ?
                ORDER BY gm.joined_at DESC
                """,
                (rs, i) -> new GroupInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("course_name")),
                studentId);

        // A1: 3 bài học gần nhất
        List<RecentLessonInfo> recentLessons = jdbc.query("""
                SELECT l.title  AS lesson_title,
                       ch.title AS chapter_title,
                       c.title  AS course_name,
                       lp.status,
                       TO_CHAR(lp.last_accessed_at, 'DD/MM/YYYY HH24:MI') AS last_accessed_at
                FROM lesson_progress lp
                JOIN lessons  l  ON l.id  = lp.lesson_id
                JOIN chapters ch ON ch.id = l.chapter_id
                JOIN courses  c  ON c.id  = lp.course_id
                WHERE lp.student_id = ?
                  AND lp.last_accessed_at IS NOT NULL
                ORDER BY lp.last_accessed_at DESC
                LIMIT 3
                """,
                (rs, i) -> new RecentLessonInfo(
                        rs.getString("lesson_title"),
                        rs.getString("chapter_title"),
                        rs.getString("course_name"),
                        rs.getString("status"),
                        rs.getString("last_accessed_at")),
                studentId);

        // A2: 3 kết quả quiz gần nhất
        List<QuizResultInfo> recentQuizResults = jdbc.query("""
                SELECT q.title      AS quiz_title,
                       c.title      AS course_name,
                       qa.score,
                       q.total_points AS max_score,
                       qa.is_passed,
                       TO_CHAR(qa.submitted_at, 'DD/MM/YYYY HH24:MI') AS submitted_at
                FROM quiz_attempts qa
                JOIN quizzes q ON q.id  = qa.quiz_id
                JOIN courses  c ON c.id = qa.course_id
                WHERE qa.student_id = ?
                  AND qa.status = 'GRADED'
                  AND qa.submitted_at IS NOT NULL
                ORDER BY qa.submitted_at DESC
                LIMIT 3
                """,
                (rs, i) -> new QuizResultInfo(
                        rs.getString("quiz_title"),
                        rs.getString("course_name"),
                        rs.getDouble("score"),
                        rs.getDouble("max_score"),
                        rs.getBoolean("is_passed"),
                        rs.getString("submitted_at")),
                studentId);

        // A3: bài tập chưa nộp
        List<UnsubmittedAssignmentInfo> unsubmitted = jdbc.query("""
                SELECT a.title, c.title AS course_name,
                       TO_CHAR(a.deadline, 'DD/MM/YYYY HH24:MI') AS deadline,
                       (a.deadline < NOW()) AS is_overdue
                FROM assignments a
                JOIN courses c ON c.id = a.course_id
                JOIN course_enrollments ce ON ce.course_id = a.course_id
                                          AND ce.student_id = ?
                WHERE a.status = 'PUBLISHED'
                  AND NOT EXISTS (
                      SELECT 1 FROM assignment_submissions s
                      WHERE s.assignment_id = a.id
                        AND s.student_id = ?
                  )
                ORDER BY a.deadline ASC
                """,
                (rs, i) -> new UnsubmittedAssignmentInfo(
                        rs.getString("title"),
                        rs.getString("course_name"),
                        rs.getString("deadline"),
                        rs.getBoolean("is_overdue")),
                studentId, studentId);

        return new UserContext(
                studentId, fullName, UserRole.STUDENT,
                courses, deadlines, groups,
                recentLessons, recentQuizResults, unsubmitted,
                List.of(), List.of(), null);
    }

    // ── INSTRUCTOR ───────────────────────────────────────────────────────────

    private UserContext buildInstructorContext(UUID instructorId, String fullName) {
        List<CourseInfo> courses = jdbc.query("""
                SELECT c.id, c.title, c.status AS progress_status,
                       (SELECT COUNT(*) FROM course_enrollments WHERE course_id = c.id) AS enrollment_count
                FROM courses c
                WHERE c.instructor_id = ?
                ORDER BY c.created_at DESC
                """,
                (rs, i) -> new CourseInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("progress_status"),
                        rs.getDouble("enrollment_count")),
                instructorId);

        List<DeadlineInfo> deadlines = jdbc.query("""
                SELECT a.title, c.title AS course_name,
                       TO_CHAR(a.deadline, 'DD/MM/YYYY HH24:MI') AS deadline,
                       (a.deadline < NOW()) AS is_late
                FROM assignments a
                JOIN courses c ON c.id = a.course_id
                WHERE c.instructor_id = ?
                  AND a.status = 'PUBLISHED'
                  AND a.deadline BETWEEN NOW() - INTERVAL '3 days' AND NOW() + INTERVAL '14 days'
                ORDER BY a.deadline ASC
                """,
                (rs, i) -> new DeadlineInfo(
                        rs.getString("title"),
                        rs.getString("course_name"),
                        rs.getString("deadline"),
                        rs.getBoolean("is_late")),
                instructorId);

        List<GroupInfo> groups = jdbc.query("""
                SELECT sg.id, sg.name, c.title AS course_name
                FROM study_groups sg
                JOIN courses c ON c.id = sg.course_id
                WHERE sg.instructor_id = ?
                ORDER BY sg.created_at DESC
                """,
                (rs, i) -> new GroupInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("course_name")),
                instructorId);

        // A4: số học viên chưa nộp theo từng bài tập
        List<SubmissionGapInfo> submissionGaps = jdbc.query("""
                SELECT a.title AS assignment_title,
                       c.title AS course_name,
                       TO_CHAR(a.deadline, 'DD/MM/YYYY HH24:MI') AS deadline,
                       (SELECT COUNT(*) FROM course_enrollments WHERE course_id = c.id)::int AS total_enrolled,
                       (SELECT COUNT(DISTINCT student_id) FROM assignment_submissions
                        WHERE assignment_id = a.id)::int AS submitted
                FROM assignments a
                JOIN courses c ON c.id = a.course_id
                WHERE c.instructor_id = ?
                  AND a.status = 'PUBLISHED'
                  AND a.deadline >= NOW() - INTERVAL '7 days'
                ORDER BY a.deadline ASC
                """,
                (rs, i) -> {
                    int total     = rs.getInt("total_enrolled");
                    int submitted = rs.getInt("submitted");
                    return new SubmissionGapInfo(
                            rs.getString("assignment_title"),
                            rs.getString("course_name"),
                            rs.getString("deadline"),
                            total,
                            submitted,
                            total - submitted);
                },
                instructorId);

        // A5: học viên có tiến độ thấp (< 20%, đã enroll > 7 ngày)
        List<AtRiskStudentInfo> atRisk = jdbc.query("""
                SELECT u.full_name AS student_name,
                       c.title    AS course_name,
                       COALESCE(cp.overall_percentage, 0)                    AS progress_pct,
                       EXTRACT(DAY FROM NOW() - ce.enrolled_at)::int         AS days_enrolled
                FROM course_enrollments ce
                JOIN users    u  ON u.id  = ce.student_id
                JOIN courses  c  ON c.id  = ce.course_id
                LEFT JOIN course_progress cp ON cp.student_id = ce.student_id
                                             AND cp.course_id  = ce.course_id
                WHERE c.instructor_id = ?
                  AND ce.enrolled_at < NOW() - INTERVAL '7 days'
                  AND COALESCE(cp.overall_percentage, 0) < 20
                ORDER BY cp.overall_percentage ASC NULLS FIRST
                LIMIT 10
                """,
                (rs, i) -> new AtRiskStudentInfo(
                        rs.getString("student_name"),
                        rs.getString("course_name"),
                        rs.getDouble("progress_pct"),
                        rs.getInt("days_enrolled")),
                instructorId);

        return new UserContext(
                instructorId, fullName, UserRole.INSTRUCTOR,
                courses, deadlines, groups,
                List.of(), List.of(), List.of(),
                submissionGaps, atRisk, null);
    }

    // ── ADMIN ─────────────────────────────────────────────────────────────────

    private UserContext buildAdminContext(UUID adminId, String fullName) {
        // A6: thống kê tổng quan hệ thống
        Map<String, Object> stats = jdbc.queryForMap("""
                SELECT
                    (SELECT COUNT(*) FROM users)                                     AS total_users,
                    (SELECT COUNT(*) FROM users WHERE role = 'STUDENT')              AS total_students,
                    (SELECT COUNT(*) FROM users WHERE role = 'INSTRUCTOR')           AS total_instructors,
                    (SELECT COUNT(*) FROM courses)                                   AS total_courses,
                    (SELECT COUNT(*) FROM courses WHERE status = 'PUBLISHED')        AS published_courses,
                    (SELECT COUNT(*) FROM course_enrollments)                        AS total_enrollments,
                    (SELECT COUNT(*) FROM ai_conversations WHERE status = 'ACTIVE')  AS active_conversations
                """);

        AdminStats adminStats = new AdminStats(
                toLong(stats.get("total_users")),
                toLong(stats.get("total_students")),
                toLong(stats.get("total_instructors")),
                toLong(stats.get("total_courses")),
                toLong(stats.get("published_courses")),
                toLong(stats.get("total_enrollments")),
                toLong(stats.get("active_conversations")));

        return new UserContext(
                adminId, fullName, UserRole.ADMIN,
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), adminStats);
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }
}
