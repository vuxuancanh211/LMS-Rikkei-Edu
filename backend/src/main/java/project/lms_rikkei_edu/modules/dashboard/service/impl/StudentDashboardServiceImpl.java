package project.lms_rikkei_edu.modules.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.dashboard.dto.response.*;
import project.lms_rikkei_edu.modules.dashboard.service.StudentDashboardService;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StudentDashboardServiceImpl implements StudentDashboardService {

    private static final String COL_TITLE = "title";
    private static final String COL_COURSE_ID = "course_id";
    private static final String COL_DEADLINE_STR = "deadline_str";
    private static final String COL_STATUS = "status";
    private static final String NO_DEADLINE = "Không thời hạn";

    private final JdbcTemplate jdbc;

    @Override
    public StudentDashboardStatsResponse getStats(UUID studentId) {
        List<StudentDashboardResponse.DueAssignmentDto> dueAssignments = getDueAssignments(studentId);
        List<Double> weeklyStudyHours = getWeeklyStudyHours(studentId);
        return getStatsInternal(studentId, dueAssignments, weeklyStudyHours);
    }

    private StudentDashboardStatsResponse getStatsInternal(UUID studentId,
                                                           List<StudentDashboardResponse.DueAssignmentDto> dueAssignments,
                                                           List<Double> weeklyStudyHours) {
        String studentName = jdbc.queryForObject(
                "SELECT COALESCE(full_name, 'Học viên') FROM users WHERE id = ?",
                String.class,
                studentId
        );
        if (studentName == null) studentName = "Học viên";

        Map<String, Integer> courseProgressCounts = java.util.Optional.ofNullable(jdbc.query("""
                SELECT
                    COUNT(CASE WHEN cp.status IS NULL OR cp.status != 'COMPLETED' THEN 1 END) AS active_cnt,
                    COUNT(CASE WHEN cp.overall_percentage >= 70 AND cp.status != 'COMPLETED' THEN 1 END) AS near_cnt
                FROM course_enrollments ce
                LEFT JOIN course_progress cp ON cp.course_id = ce.course_id AND cp.student_id = ce.student_id
                WHERE ce.student_id = ?
                """, rs -> {
            if (rs.next()) {
                return Map.of(
                        "active", rs.getInt("active_cnt"),
                        "near", rs.getInt("near_cnt")
                );
            }
            return Map.of("active", 0, "near", 0);
        }, studentId)).orElseGet(Map::of);
        int activeCourses = courseProgressCounts.getOrDefault("active", 0);
        int nearCompletion = courseProgressCounts.getOrDefault("near", 0);

        Integer certificates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM certificates WHERE student_id = ?",
                Integer.class,
                studentId
        );
        if (certificates == null) certificates = 0;

        int pendingTasksCount = dueAssignments.size();
        int dueSoonTasksCount = (int) dueAssignments.stream()
                .filter(a -> "pending".equals(a.getStatus()))
                .count();

        double totalHours = weeklyStudyHours.stream().mapToDouble(Double::doubleValue).sum();
        double roundedHours = Math.round(totalHours * 10.0) / 10.0;

        StudentDashboardResponse.StatsDto stats = StudentDashboardResponse.StatsDto.builder()
                .activeCoursesCount(activeCourses)
                .nearCompletionCoursesCount(nearCompletion)
                .pendingTasksCount(pendingTasksCount)
                .dueSoonTasksCount(dueSoonTasksCount)
                .certificatesCount(certificates)
                .weeklyHours(roundedHours)
                .weeklyHoursTrend(roundedHours > 0 ? 12.0 : null)
                .build();

        return StudentDashboardStatsResponse.builder()
                .studentName(studentName)
                .stats(stats)
                .build();
    }

    @Override
    public List<StudentDashboardResponse.CourseSummaryDto> getInProgressCourses(UUID studentId) {
        return jdbc.query("""
                SELECT c.id, c.title, c.thumbnail_url, cat.name AS category_name, COALESCE(cp.overall_percentage, 0) AS progress
                FROM course_enrollments ce
                JOIN courses c ON c.id = ce.course_id
                LEFT JOIN course_categories cat ON cat.id = c.category_id
                LEFT JOIN course_progress cp ON cp.course_id = ce.course_id AND cp.student_id = ce.student_id
                WHERE ce.student_id = ?
                  AND (cp.status IS NULL OR cp.status != 'COMPLETED')
                ORDER BY ce.enrolled_at DESC
                LIMIT 5
                """,
                (rs, i) -> StudentDashboardResponse.CourseSummaryDto.builder()
                        .id(rs.getObject("id", UUID.class))
                        .title(rs.getString(COL_TITLE))
                        .category(rs.getString("category_name") != null ? rs.getString("category_name") : "Khóa học")
                        .thumbnailUrl(rs.getString("thumbnail_url") != null ? rs.getString("thumbnail_url") : "assets/courses/placeholder.png")
                        .progress(rs.getInt("progress"))
                        .build(),
                studentId
        );
    }

    @Override
    public List<StudentDashboardResponse.DueAssignmentDto> getDueAssignments(UUID studentId) {
        return jdbc.query("""
                SELECT a.id, a.course_id, a.title,
                       TO_CHAR(a.deadline, 'DD/MM/YYYY') AS deadline_str,
                       CASE WHEN a.deadline < NOW() THEN 'late' ELSE 'assignment_pending' END AS status
                FROM assignments a
                JOIN course_enrollments ce ON ce.course_id = a.course_id AND ce.student_id = ?
                WHERE a.status = 'PUBLISHED'
                  AND COALESCE(LOWER(a.title), '') NOT LIKE '%quiz%'
                  AND NOT EXISTS (
                      SELECT 1 FROM assignment_submissions s WHERE s.assignment_id = a.id AND s.student_id = ?
                  )
                ORDER BY COALESCE(a.deadline, '9999-12-31'::timestamp) ASC
                LIMIT 5
                """,
                (rs, i) -> {
                    String title = rs.getString(COL_TITLE);
                    return StudentDashboardResponse.DueAssignmentDto.builder()
                            .id(rs.getObject("id", UUID.class))
                            .courseId(rs.getObject(COL_COURSE_ID, UUID.class))
                            .title(title)
                            .type("file")
                            .deadline(rs.getString(COL_DEADLINE_STR) != null ? rs.getString(COL_DEADLINE_STR) : NO_DEADLINE)
                            .status(rs.getString(COL_STATUS))
                            .build();
                },
                studentId, studentId
        );
    }

    @Override
    public List<StudentDashboardResponse.DueAssignmentDto> getDueQuizzes(UUID studentId) {
        List<StudentDashboardResponse.DueAssignmentDto> list = new ArrayList<>();
        jdbc.query("""
                SELECT q.id, q.course_id, q.title,
                       TO_CHAR(q.end_date, 'DD/MM/YYYY') AS deadline_str,
                       CASE WHEN q.end_date < NOW() THEN 'late' ELSE 'quiz_pending' END AS status
                FROM quizzes q
                JOIN course_enrollments ce ON ce.course_id = q.course_id AND ce.student_id = ?
                WHERE q.status = 'PUBLISHED'
                  AND NOT EXISTS (
                      SELECT 1 FROM quiz_attempts qa WHERE qa.quiz_id = q.id AND qa.student_id = ? AND (qa.is_passed = true OR qa.status IN ('SUBMITTED', 'GRADED'))
                  )
                ORDER BY COALESCE(q.end_date, '9999-12-31'::timestamp) ASC
                LIMIT 5
                """,
                (rs, i) -> StudentDashboardResponse.DueAssignmentDto.builder()
                        .id(rs.getObject("id", UUID.class))
                        .courseId(rs.getObject(COL_COURSE_ID, UUID.class))
                        .title(rs.getString(COL_TITLE))
                        .type("quiz")
                        .deadline(rs.getString(COL_DEADLINE_STR) != null ? rs.getString(COL_DEADLINE_STR) : NO_DEADLINE)
                        .status(rs.getString(COL_STATUS))
                        .build(),
                studentId, studentId
        ).forEach(list::add);

        if (list.size() < 5) {
            jdbc.query("""
SELECT a.id, a.course_id, a.title,
                           TO_CHAR(a.deadline, 'DD/MM/YYYY') AS deadline_str,
                           CASE WHEN a.deadline < NOW() THEN 'late' ELSE 'quiz_pending' END AS status
                    FROM assignments a
                    JOIN course_enrollments ce ON ce.course_id = a.course_id AND ce.student_id = ?
                    WHERE a.status = 'PUBLISHED'
                      AND LOWER(a.title) LIKE '%quiz%'
                      AND NOT EXISTS (
                          SELECT 1 FROM assignment_submissions s WHERE s.assignment_id = a.id AND s.student_id = ?
                      )
                    ORDER BY COALESCE(a.deadline, '9999-12-31'::timestamp) ASC
                    LIMIT ?
                    """,
                    (rs, i) -> StudentDashboardResponse.DueAssignmentDto.builder()
                            .id(rs.getObject("id", UUID.class))
                            .courseId(rs.getObject(COL_COURSE_ID, UUID.class))
                            .title(rs.getString(COL_TITLE))
                            .type("quiz")
                            .deadline(rs.getString(COL_DEADLINE_STR) != null ? rs.getString(COL_DEADLINE_STR) : NO_DEADLINE)
                            .status(rs.getString(COL_STATUS))
                            .build(),
                    studentId, studentId, 5 - list.size()
            ).forEach(list::add);
        }
        return list;
    }

    @Override
    public List<Double> getWeeklyStudyHours(UUID studentId) {
        List<Double> weeklyStudyHours = new ArrayList<>(Collections.nCopies(7, 0.0));
        jdbc.query("""
                SELECT EXTRACT(ISODOW FROM last_accessed_at) AS dow,
                       COALESCE(SUM(LEAST(COALESCE(document_view_seconds, last_playback_position, watched_percentage * 36, 1800), 7200)) / 3600.0, 0) AS hours
                FROM lesson_progress
                WHERE student_id = ?
                  AND last_accessed_at >= DATE_TRUNC('week', NOW())
                GROUP BY EXTRACT(ISODOW FROM last_accessed_at)
                """,
                rs -> {
                    int dow = rs.getInt("dow");
                    double hours = Math.round(rs.getDouble("hours") * 10.0) / 10.0;
                    if (dow >= 1 && dow <= 7) {
                        weeklyStudyHours.set(dow - 1, hours);
                    }
                },
                studentId
        );
        return weeklyStudyHours;
    }

    @Override
    public List<StudentDashboardResponse.SkillProgressDto> getSkillProgress(UUID studentId) {
        return jdbc.query("""
                SELECT cat.name AS title,
                       COALESCE(AVG(COALESCE(cp.overall_percentage, 0)), 0) AS progress
                FROM course_categories cat
                LEFT JOIN courses c ON c.category_id = cat.id
                LEFT JOIN course_enrollments ce ON ce.course_id = c.id AND ce.student_id = ?
                LEFT JOIN course_progress cp ON cp.course_id = ce.course_id AND cp.student_id = ce.student_id
                WHERE cat.is_active = true
                GROUP BY cat.name, cat.created_at
                ORDER BY progress DESC, cat.created_at ASC
                LIMIT 5
                """,
                (rs, i) -> StudentDashboardResponse.SkillProgressDto.builder()
                        .title(rs.getString(COL_TITLE))
                        .progress((int) Math.round(rs.getDouble("progress")))
                        .build(),
                studentId
        );
    }

    @Override
    public StudentDashboardResponse getStudentDashboard(UUID studentId) {
        List<StudentDashboardResponse.DueAssignmentDto> dueAssignments = getDueAssignments(studentId);
        List<Double> weeklyStudyHours = getWeeklyStudyHours(studentId);
        StudentDashboardStatsResponse statsResp = getStatsInternal(studentId, dueAssignments, weeklyStudyHours);
        List<StudentDashboardResponse.CourseSummaryDto> inProgressCourses = getInProgressCourses(studentId);
        List<StudentDashboardResponse.SkillProgressDto> skillProgress = getSkillProgress(studentId);

        return StudentDashboardResponse.builder()
                .studentName(statsResp.getStudentName())
                .stats(statsResp.getStats())
                .inProgressCourses(inProgressCourses)
                .dueAssignments(dueAssignments)
                .weeklyStudyHours(weeklyStudyHours)
                .skillProgress(skillProgress)
                .build();
    }
}
