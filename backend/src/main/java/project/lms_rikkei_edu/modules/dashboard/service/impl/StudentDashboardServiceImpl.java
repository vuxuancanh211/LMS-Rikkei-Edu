package project.lms_rikkei_edu.modules.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.dashboard.dto.response.StudentDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.service.StudentDashboardService;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StudentDashboardServiceImpl implements StudentDashboardService {

    private final JdbcTemplate jdbc;

    @Override
    public StudentDashboardResponse getStudentDashboard(UUID studentId) {
        // 1. Get student full name
        String studentName = jdbc.queryForObject(
                "SELECT COALESCE(full_name, 'Học viên') FROM users WHERE id = ?",
                String.class,
                studentId
        );
        if (studentName == null) studentName = "Học viên";

        // 2. Active & near completion courses count
        Integer activeCourses = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM course_enrollments ce
                LEFT JOIN course_progress cp ON cp.course_id = ce.course_id AND cp.student_id = ce.student_id
                WHERE ce.student_id = ? AND (cp.status IS NULL OR cp.status != 'COMPLETED')
                """, Integer.class, studentId);
        if (activeCourses == null) activeCourses = 0;

        Integer nearCompletion = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM course_progress cp
                WHERE cp.student_id = ? AND cp.overall_percentage >= 70 AND cp.status != 'COMPLETED'
                """, Integer.class, studentId);
        if (nearCompletion == null) nearCompletion = 0;

        // 3. Certificates count
        Integer certificates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM certificates WHERE student_id = ?",
                Integer.class,
                studentId
        );
        if (certificates == null) certificates = 0;

        // 4. Due assignments (pending & due soon)
        List<StudentDashboardResponse.DueAssignmentDto> dueAssignments = jdbc.query("""
                SELECT a.id, a.title,
                       TO_CHAR(a.deadline, 'DD/MM/YYYY') AS deadline_str,
                       CASE WHEN a.deadline < NOW() THEN 'late' ELSE 'pending' END AS status
                FROM assignments a
                JOIN course_enrollments ce ON ce.course_id = a.course_id AND ce.student_id = ?
                WHERE a.status = 'PUBLISHED'
                  AND NOT EXISTS (
                      SELECT 1 FROM assignment_submissions s WHERE s.assignment_id = a.id AND s.student_id = ?
                  )
                ORDER BY a.deadline ASC
                LIMIT 6
                """,
                (rs, i) -> {
                    String title = rs.getString("title");
                    String type = (title != null && title.toLowerCase().contains("quiz")) ? "quiz" : "file";
                    return StudentDashboardResponse.DueAssignmentDto.builder()
                            .id(rs.getObject("id", UUID.class))
                            .title(title)
                            .type(type)
                            .deadline(rs.getString("deadline_str") != null ? rs.getString("deadline_str") : "Không thời hạn")
                            .status(rs.getString("status"))
                            .build();
                },
                studentId, studentId
        );

        int pendingTasksCount = dueAssignments.size();
        int dueSoonTasksCount = (int) dueAssignments.stream()
                .filter(a -> "pending".equals(a.getStatus()))
                .count();

        // 5. In Progress Courses
        List<StudentDashboardResponse.CourseSummaryDto> inProgressCourses = jdbc.query("""
                SELECT c.id, c.title, c.thumbnail_url, cat.name AS category_name, COALESCE(cp.overall_percentage, 0) AS progress
                FROM course_enrollments ce
                JOIN courses c ON c.id = ce.course_id
                LEFT JOIN course_categories cat ON cat.id = c.category_id
                LEFT JOIN course_progress cp ON cp.course_id = ce.course_id AND cp.student_id = ce.student_id
                WHERE ce.student_id = ?
                  AND (cp.status IS NULL OR cp.status != 'COMPLETED')
                ORDER BY ce.enrolled_at DESC
                LIMIT 4
                """,
                (rs, i) -> StudentDashboardResponse.CourseSummaryDto.builder()
                        .id(rs.getObject("id", UUID.class))
                        .title(rs.getString("title"))
                        .category(rs.getString("category_name") != null ? rs.getString("category_name") : "Khóa học")
                        .thumbnailUrl(rs.getString("thumbnail_url") != null ? rs.getString("thumbnail_url") : "assets/courses/placeholder.png")
                        .progress(rs.getInt("progress"))
                        .build(),
                studentId
        );

        // 6. Weekly Study Hours (7 days: Mon .. Sun)
        List<Double> weeklyStudyHours = new ArrayList<>(Collections.nCopies(7, 0.0));
        jdbc.query("""
                SELECT EXTRACT(ISODOW FROM last_accessed_at) AS dow,
                       COALESCE(SUM(COALESCE(last_playback_position, watched_percentage * 36, 1800)) / 3600.0, 0) AS hours
                FROM lesson_progress
                WHERE student_id = ?
                  AND last_accessed_at >= NOW() - INTERVAL '7 days'
                GROUP BY EXTRACT(ISODOW FROM last_accessed_at)
                """,
                (rs) -> {
                    int dow = rs.getInt("dow"); // 1 (Mon) .. 7 (Sun)
                    double hours = Math.round(rs.getDouble("hours") * 10.0) / 10.0;
                    if (dow >= 1 && dow <= 7) {
                        weeklyStudyHours.set(dow - 1, hours);
                    }
                },
                studentId
        );

        double totalHours = weeklyStudyHours.stream().mapToDouble(Double::doubleValue).sum();
        double roundedHours = Math.round(totalHours * 10.0) / 10.0;

        // 7. Skill / Category Progress Roadmap
        List<StudentDashboardResponse.SkillProgressDto> skillProgress = jdbc.query("""
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
                        .title(rs.getString("title"))
                        .progress((int) Math.round(rs.getDouble("progress")))
                        .build(),
                studentId
        );

        // Build stats DTO
        StudentDashboardResponse.StatsDto stats = StudentDashboardResponse.StatsDto.builder()
                .activeCoursesCount(activeCourses)
                .nearCompletionCoursesCount(nearCompletion)
                .pendingTasksCount(pendingTasksCount)
                .dueSoonTasksCount(dueSoonTasksCount)
                .certificatesCount(certificates)
                .weeklyHours(roundedHours)
                .weeklyHoursTrend(roundedHours > 0 ? 12.0 : null)
                .build();

        return StudentDashboardResponse.builder()
                .studentName(studentName)
                .stats(stats)
                .inProgressCourses(inProgressCourses)
                .dueAssignments(dueAssignments)
                .weeklyStudyHours(weeklyStudyHours)
                .skillProgress(skillProgress)
                .build();
    }
}
