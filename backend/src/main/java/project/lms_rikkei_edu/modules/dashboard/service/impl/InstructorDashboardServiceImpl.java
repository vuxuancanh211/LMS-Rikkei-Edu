package project.lms_rikkei_edu.modules.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.dashboard.dto.response.*;
import project.lms_rikkei_edu.modules.dashboard.service.InstructorDashboardService;
import project.lms_rikkei_edu.modules.dashboard.util.DashboardChartUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InstructorDashboardServiceImpl implements InstructorDashboardService {

    private final JdbcTemplate jdbc;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Override
    public InstructorDashboardStatsResponse getStats(UUID instructorId) {
        Map<String, Integer> courseCounts = java.util.Optional.ofNullable(jdbc.query("""
                SELECT
                    COUNT(CASE WHEN status IN ('PUBLISHED', 'ACTIVE') THEN 1 END) AS active_cnt,
                    COUNT(CASE WHEN status = 'PENDING_APPROVAL' THEN 1 END) AS pending_cnt
                FROM courses
                WHERE instructor_id = ?
                """, rs -> {
            if (rs.next()) {
                return Map.of(
                        "active", rs.getInt("active_cnt"),
                        "pending", rs.getInt("pending_cnt")
                );
            }
            return Map.of("active", 0, "pending", 0);
        }, instructorId)).orElseGet(Map::of);
        int activeCoursesCount = courseCounts.getOrDefault("active", 0);
        int pendingCoursesCount = courseCounts.getOrDefault("pending", 0);

        Integer totalStudentsCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT ce.student_id)
                FROM course_enrollments ce
                JOIN courses c ON c.id = ce.course_id
                WHERE c.instructor_id = ?
                """, Integer.class, instructorId);
        if (totalStudentsCount == null) totalStudentsCount = 0;

        Integer totalGroupsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM study_groups WHERE instructor_id = ?",
                Integer.class, instructorId);
        if (totalGroupsCount == null) totalGroupsCount = 0;

        Integer pendingSubmissionsCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM assignment_submissions s
                JOIN courses c ON c.id = s.course_id
                WHERE c.instructor_id = ? AND s.status IN ('SUBMITTED', 'LATE')
                """, Integer.class, instructorId);
        if (pendingSubmissionsCount == null) pendingSubmissionsCount = 0;

        Double avgRate = jdbc.queryForObject("""
                SELECT COALESCE(AVG(cp.overall_percentage), 0.0)
                FROM course_progress cp
                JOIN courses c ON c.id = cp.course_id
                WHERE c.instructor_id = ?
                """, Double.class, instructorId);
        double averageCompletionRate = avgRate != null ? Math.round(avgRate * 10.0) / 10.0 : 0.0;

        return InstructorDashboardStatsResponse.builder()
                .activeCoursesCount(activeCoursesCount)
                .pendingCoursesCount(pendingCoursesCount)
                .totalStudentsCount(totalStudentsCount)
                .totalGroupsCount(totalGroupsCount)
                .pendingSubmissionsCount(pendingSubmissionsCount)
                .averageCompletionRate(averageCompletionRate)
                .build();
    }

    @Override
    public InstructorDashboardChartResponse getCompletionChart(UUID instructorId) {
        List<Double> monthlyCompletionRates = new ArrayList<>(Collections.nCopies(6, 0.0));
        List<String> monthlyLabels = DashboardChartUtils.getMonthlyLabels(6);
        Map<String, Integer> ymToIndex = DashboardChartUtils.getYearMonthToIndexMap(6);

        jdbc.query("""
                SELECT TO_CHAR(cp.updated_at, 'YYYY-MM') AS ym, COALESCE(AVG(cp.overall_percentage), 0.0) AS rate
                FROM course_progress cp
                JOIN courses c ON c.id = cp.course_id
                WHERE c.instructor_id = ?
                  AND cp.updated_at >= DATE_TRUNC('month', NOW() - INTERVAL '5 months')
                GROUP BY ym
                """, (rs) -> {
            String ym = rs.getString("ym");
            Double rate = rs.getDouble("rate");
            Integer idx = ymToIndex.get(ym);
            if (idx != null && idx >= 0 && idx < 6) {
                monthlyCompletionRates.set(idx, Math.round(rate * 10.0) / 10.0);
            }
        }, instructorId);

        return InstructorDashboardChartResponse.builder()
                .monthlyCompletionRates(monthlyCompletionRates)
                .monthlyLabels(monthlyLabels)
                .build();
    }

    @Override
    public InstructorDashboardDistributionsResponse getCourseDistributions(UUID instructorId) {
        Double avgRate = jdbc.queryForObject("""
                SELECT COALESCE(AVG(cp.overall_percentage), 0.0)
                FROM course_progress cp
                JOIN courses c ON c.id = cp.course_id
                WHERE c.instructor_id = ?
                """, Double.class, instructorId);
        double averageCompletionRate = avgRate != null ? Math.round(avgRate * 10.0) / 10.0 : 0.0;
        return getCourseDistributionsInternal(instructorId, averageCompletionRate);
    }

    private InstructorDashboardDistributionsResponse getCourseDistributionsInternal(UUID instructorId, double averageCompletionRate) {
        List<String> palette = List.of("#2563eb", "#10b981", "#f59e0b", "#7c3aed", "#ec4899");
        List<CourseDistributionDto> courseDistributions = jdbc.query("""
                SELECT c.title, COUNT(ce.student_id) AS student_count
                FROM courses c
                LEFT JOIN course_enrollments ce ON ce.course_id = c.id
                WHERE c.instructor_id = ?
                GROUP BY c.id, c.title
                ORDER BY student_count DESC, c.created_at ASC
                LIMIT 5
                """, (rs, rowNum) -> CourseDistributionDto.builder()
                .title(rs.getString("title"))
                .studentCount(rs.getInt("student_count"))
                .color(palette.get(rowNum % palette.size()))
                .build(), instructorId);

        return InstructorDashboardDistributionsResponse.builder()
                .averageCompletionRate(averageCompletionRate)
                .courseDistributions(courseDistributions)
                .build();
    }

    @Override
    public List<PendingSubmissionDto> getPendingSubmissions(UUID instructorId) {
        return jdbc.query("""
                SELECT s.id, u.full_name, a.title AS assignment_title,
                       COALESCE((SELECT sg.name FROM group_members gm JOIN study_groups sg ON sg.id = gm.group_id WHERE gm.student_id = s.student_id AND sg.course_id = s.course_id LIMIT 1), 'Tự do') AS group_name,
                       s.submitted_at, s.status
                FROM assignment_submissions s
                JOIN assignments a ON a.id = s.assignment_id
                JOIN users u ON u.id = s.student_id
                JOIN courses c ON c.id = s.course_id
                WHERE c.instructor_id = ? AND s.status IN ('SUBMITTED', 'LATE')
                ORDER BY s.submitted_at DESC
                LIMIT 5
                """, (rs, rowNum) -> {
            Instant submittedAt = rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toInstant() : Instant.now();
            return PendingSubmissionDto.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .studentName(rs.getString("full_name"))
                    .assignmentTitle(rs.getString("assignment_title"))
                    .groupName(rs.getString("group_name"))
                    .submittedAt(DATE_FORMATTER.format(submittedAt))
                    .status(rs.getString("status"))
                    .build();
        }, instructorId);
    }

    @Override
    public InstructorDashboardResponse getDashboard(UUID instructorId) {
        InstructorDashboardStatsResponse stats = getStats(instructorId);
        InstructorDashboardChartResponse chart = getCompletionChart(instructorId);
        InstructorDashboardDistributionsResponse dist = getCourseDistributionsInternal(instructorId, stats.getAverageCompletionRate());
        List<PendingSubmissionDto> submissions = getPendingSubmissions(instructorId);

        return InstructorDashboardResponse.builder()
                .activeCoursesCount(stats.getActiveCoursesCount())
                .pendingCoursesCount(stats.getPendingCoursesCount())
                .totalStudentsCount(stats.getTotalStudentsCount())
                .totalGroupsCount(stats.getTotalGroupsCount())
                .pendingSubmissionsCount(stats.getPendingSubmissionsCount())
                .monthlyCompletionRates(chart.getMonthlyCompletionRates())
                .monthlyLabels(chart.getMonthlyLabels())
                .averageCompletionRate(stats.getAverageCompletionRate())
                .courseDistributions(dist.getCourseDistributions())
                .pendingSubmissions(submissions)
                .build();
    }
}
