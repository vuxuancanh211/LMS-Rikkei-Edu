package project.lms_rikkei_edu.modules.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.dashboard.dto.response.*;
import project.lms_rikkei_edu.modules.dashboard.service.AdminDashboardService;
import project.lms_rikkei_edu.modules.dashboard.util.DashboardChartUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final JdbcTemplate jdbc;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Override
    public AdminDashboardStatsResponse getStats() {
        Map<String, Integer> userCounts = java.util.Optional.ofNullable(jdbc.query("""
                SELECT
                    COUNT(CASE WHEN role = 'STUDENT' THEN 1 END) AS students,
                    COUNT(CASE WHEN role = 'INSTRUCTOR' THEN 1 END) AS instructors
                FROM users
                """, rs -> {
            if (rs.next()) {
                return Map.of(
                        "students", rs.getInt("students"),
                        "instructors", rs.getInt("instructors")
                );
            }
            return Map.of("students", 0, "instructors", 0);
        })).orElseGet(Map::of);
        int totalStudentsCount = userCounts.getOrDefault("students", 0);
        int totalInstructorsCount = userCounts.getOrDefault("instructors", 0);

        Integer activeCoursesCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM courses WHERE status IN ('PUBLISHED', 'APPROVED')", Integer.class);
        if (activeCoursesCount == null) activeCoursesCount = 0;

        Double avgRate = jdbc.queryForObject(
                "SELECT COALESCE(AVG(overall_percentage), 0.0) FROM course_progress", Double.class);
        double averageCompletionRate = avgRate != null ? Math.round(avgRate * 10.0) / 10.0 : 0.0;

        return AdminDashboardStatsResponse.builder()
                .totalStudentsCount(totalStudentsCount)
                .totalInstructorsCount(totalInstructorsCount)
                .activeCoursesCount(activeCoursesCount)
                .averageCompletionRate(averageCompletionRate)
                .build();
    }

    @Override
    public AdminDashboardTrafficResponse getTrafficChart() {
        List<Double> trafficData = new ArrayList<>(Collections.nCopies(6, 0.0));
        List<String> trafficLabels = DashboardChartUtils.getMonthlyLabels(6);
        Map<String, Integer> ymToIndex = DashboardChartUtils.getYearMonthToIndexMap(6);

        jdbc.query("""
                SELECT TO_CHAR(COALESCE(last_accessed_at, first_accessed_at), 'YYYY-MM') AS ym, COUNT(*) AS cnt
                FROM lesson_progress
                WHERE COALESCE(last_accessed_at, first_accessed_at) >= DATE_TRUNC('month', NOW() - INTERVAL '5 months')
                GROUP BY ym
                """, (rs) -> {
            String ym = rs.getString("ym");
            Double cnt = rs.getDouble("cnt");
            Integer idx = ymToIndex.get(ym);
            if (idx != null && idx >= 0 && idx < 6) {
                trafficData.set(idx, cnt);
            }
        });

        List<Double> weeklyTrafficData = new ArrayList<>(Collections.nCopies(7, 0.0));
        List<String> weeklyTrafficLabels = DashboardChartUtils.getWeeklyLabels(7);
        Map<String, Integer> dateToIndex = DashboardChartUtils.getDateToIndexMap(7);

        jdbc.query("""
                SELECT TO_CHAR(COALESCE(last_accessed_at, first_accessed_at), 'YYYY-MM-DD') AS dstr, COUNT(*) AS cnt
                FROM lesson_progress
                WHERE COALESCE(last_accessed_at, first_accessed_at) >= NOW() - INTERVAL '7 days'
                GROUP BY dstr
                """, (rs) -> {
            String dstr = rs.getString("dstr");
            Double cnt = rs.getDouble("cnt");
            Integer idx = dateToIndex.get(dstr);
            if (idx != null && idx >= 0 && idx < 7) {
                weeklyTrafficData.set(idx, cnt);
            }
        });

        return AdminDashboardTrafficResponse.builder()
                .trafficData(trafficData)
                .trafficLabels(trafficLabels)
                .weeklyTrafficData(weeklyTrafficData)
                .weeklyTrafficLabels(weeklyTrafficLabels)
                .build();
    }

    @Override
    public AdminDashboardCoursesChartResponse getCoursesChart() {
        List<Integer> newCoursesData = new ArrayList<>(Collections.nCopies(6, 0));
        List<String> newCoursesLabels = DashboardChartUtils.getMonthlyLabels(6);
        Map<String, Integer> ymToIndex = DashboardChartUtils.getYearMonthToIndexMap(6);

        jdbc.query("""
                SELECT TO_CHAR(created_at, 'YYYY-MM') AS ym, COUNT(*) AS cnt
                FROM courses
                WHERE created_at >= DATE_TRUNC('month', NOW() - INTERVAL '5 months')
                GROUP BY ym
                """, (rs) -> {
            String ym = rs.getString("ym");
            Integer cnt = rs.getInt("cnt");
            Integer idx = ymToIndex.get(ym);
            if (idx != null && idx >= 0 && idx < 6) {
                newCoursesData.set(idx, cnt);
            }
        });

        List<Integer> weeklyCoursesData = new ArrayList<>(Collections.nCopies(7, 0));
        List<String> weeklyCoursesLabels = DashboardChartUtils.getWeeklyLabels(7);
        Map<String, Integer> dateToIndex = DashboardChartUtils.getDateToIndexMap(7);

        jdbc.query("""
                SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS dstr, COUNT(*) AS cnt
                FROM courses
                WHERE created_at >= NOW() - INTERVAL '7 days'
                GROUP BY dstr
                """, (rs) -> {
            String dstr = rs.getString("dstr");
            Integer cnt = rs.getInt("cnt");
            Integer idx = dateToIndex.get(dstr);
            if (idx != null && idx >= 0 && idx < 7) {
                weeklyCoursesData.set(idx, cnt);
            }
        });

        return AdminDashboardCoursesChartResponse.builder()
                .newCoursesData(newCoursesData)
                .newCoursesLabels(newCoursesLabels)
                .weeklyCoursesData(weeklyCoursesData)
                .weeklyCoursesLabels(weeklyCoursesLabels)
                .build();
    }

    @Override
    public AdminDashboardUsersChartResponse getUsersChart() {
        List<Integer> newUsersData = new ArrayList<>(Collections.nCopies(6, 0));
        List<String> newUsersLabels = DashboardChartUtils.getMonthlyLabels(6);
        Map<String, Integer> ymToIndex = DashboardChartUtils.getYearMonthToIndexMap(6);

        jdbc.query("""
                SELECT TO_CHAR(created_at, 'YYYY-MM') AS ym, COUNT(*) AS cnt
                FROM users
                WHERE created_at >= DATE_TRUNC('month', NOW() - INTERVAL '5 months')
                GROUP BY ym
                """, (rs) -> {
            String ym = rs.getString("ym");
            Integer cnt = rs.getInt("cnt");
            Integer idx = ymToIndex.get(ym);
            if (idx != null && idx >= 0 && idx < 6) {
                newUsersData.set(idx, cnt);
            }
        });

        List<Integer> weeklyUsersData = new ArrayList<>(Collections.nCopies(7, 0));
        List<String> weeklyUsersLabels = DashboardChartUtils.getWeeklyLabels(7);
        Map<String, Integer> dateToIndex = DashboardChartUtils.getDateToIndexMap(7);

        jdbc.query("""
                SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS dstr, COUNT(*) AS cnt
                FROM users
                WHERE created_at >= NOW() - INTERVAL '7 days'
                GROUP BY dstr
                """, (rs) -> {
            String dstr = rs.getString("dstr");
            Integer cnt = rs.getInt("cnt");
            Integer idx = dateToIndex.get(dstr);
            if (idx != null && idx >= 0 && idx < 7) {
                weeklyUsersData.set(idx, cnt);
            }
        });

        return AdminDashboardUsersChartResponse.builder()
                .newUsersData(newUsersData)
                .newUsersLabels(newUsersLabels)
                .weeklyUsersData(weeklyUsersData)
                .weeklyUsersLabels(weeklyUsersLabels)
                .build();
    }

    @Override
    public AdminDashboardEnrollmentsChartResponse getEnrollmentsChart() {
        List<Integer> enrollmentsData = new ArrayList<>(Collections.nCopies(6, 0));
        List<String> enrollmentsLabels = DashboardChartUtils.getMonthlyLabels(6);
        Map<String, Integer> ymToIndex = DashboardChartUtils.getYearMonthToIndexMap(6);

        jdbc.query("""
                SELECT TO_CHAR(enrolled_at, 'YYYY-MM') AS ym, COUNT(*) AS cnt
                FROM course_enrollments
                WHERE enrolled_at >= DATE_TRUNC('month', NOW() - INTERVAL '5 months')
                GROUP BY ym
                """, (rs) -> {
            String ym = rs.getString("ym");
            Integer cnt = rs.getInt("cnt");
            Integer idx = ymToIndex.get(ym);
            if (idx != null && idx >= 0 && idx < 6) {
                enrollmentsData.set(idx, cnt);
            }
        });

        List<Integer> weeklyEnrollmentsData = new ArrayList<>(Collections.nCopies(7, 0));
        List<String> weeklyEnrollmentsLabels = DashboardChartUtils.getWeeklyLabels(7);
        Map<String, Integer> dateToIndex = DashboardChartUtils.getDateToIndexMap(7);

        jdbc.query("""
                SELECT TO_CHAR(enrolled_at, 'YYYY-MM-DD') AS dstr, COUNT(*) AS cnt
                FROM course_enrollments
                WHERE enrolled_at >= NOW() - INTERVAL '7 days'
                GROUP BY dstr
                """, (rs) -> {
            String dstr = rs.getString("dstr");
            Integer cnt = rs.getInt("cnt");
            Integer idx = dateToIndex.get(dstr);
            if (idx != null && idx >= 0 && idx < 7) {
                weeklyEnrollmentsData.set(idx, cnt);
            }
        });

        return AdminDashboardEnrollmentsChartResponse.builder()
                .enrollmentsData(enrollmentsData)
                .enrollmentsLabels(enrollmentsLabels)
                .weeklyEnrollmentsData(weeklyEnrollmentsData)
                .weeklyEnrollmentsLabels(weeklyEnrollmentsLabels)
                .build();
    }

    @Override
    public List<PendingApprovalDto> getPendingApprovals() {
        return jdbc.query("""
                SELECT c.id, c.title AS course_title, u.full_name AS instructor_name, c.created_at, c.status
                FROM courses c
                JOIN users u ON u.id = c.instructor_id
                WHERE c.status IN ('PENDING', 'PENDING_UPDATE')
                ORDER BY c.created_at DESC
                LIMIT 5
                """, (rs, rowNum) -> {
            Instant createdAt = rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now();
            return PendingApprovalDto.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .courseName(rs.getString("course_title"))
                    .instructorName(rs.getString("instructor_name"))
                    .submittedDate(DATE_FORMATTER.format(createdAt))
                    .status(rs.getString("status"))
                    .build();
        });
    }

    @Override
    public AdminDashboardResponse getDashboard() {
        AdminDashboardStatsResponse stats = getStats();
        AdminDashboardTrafficResponse traffic = getTrafficChart();
        AdminDashboardCoursesChartResponse courses = getCoursesChart();
        AdminDashboardUsersChartResponse users = getUsersChart();
        AdminDashboardEnrollmentsChartResponse enrollments = getEnrollmentsChart();
        List<PendingApprovalDto> pendingApprovals = getPendingApprovals();

        return AdminDashboardResponse.builder()
                .totalStudentsCount(stats.getTotalStudentsCount())
                .totalInstructorsCount(stats.getTotalInstructorsCount())
                .activeCoursesCount(stats.getActiveCoursesCount())
                .averageCompletionRate(stats.getAverageCompletionRate())
                .trafficData(traffic.getTrafficData())
                .trafficLabels(traffic.getTrafficLabels())
                .newCoursesData(courses.getNewCoursesData())
                .newCoursesLabels(courses.getNewCoursesLabels())
                .weeklyTrafficData(traffic.getWeeklyTrafficData())
                .weeklyTrafficLabels(traffic.getWeeklyTrafficLabels())
                .weeklyCoursesData(courses.getWeeklyCoursesData())
                .weeklyCoursesLabels(courses.getWeeklyCoursesLabels())
                .newUsersData(users.getNewUsersData())
                .newUsersLabels(users.getNewUsersLabels())
                .weeklyUsersData(users.getWeeklyUsersData())
                .weeklyUsersLabels(users.getWeeklyUsersLabels())
                .enrollmentsData(enrollments.getEnrollmentsData())
                .enrollmentsLabels(enrollments.getEnrollmentsLabels())
                .weeklyEnrollmentsData(enrollments.getWeeklyEnrollmentsData())
                .weeklyEnrollmentsLabels(enrollments.getWeeklyEnrollmentsLabels())
                .pendingApprovals(pendingApprovals)
                .build();
    }
}
