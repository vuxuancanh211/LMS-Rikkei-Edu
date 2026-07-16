package project.lms_rikkei_edu.modules.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.dashboard.dto.response.*;
import project.lms_rikkei_edu.modules.dashboard.service.AdminDashboardService;

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
        Integer totalStudentsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'STUDENT'", Integer.class);
        if (totalStudentsCount == null) totalStudentsCount = 0;

        Integer totalInstructorsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'INSTRUCTOR'", Integer.class);
        if (totalInstructorsCount == null) totalInstructorsCount = 0;

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

    private static final String[] EN_MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] EN_DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    @Override
    public AdminDashboardTrafficResponse getTrafficChart() {
        List<Double> trafficData = new ArrayList<>(Collections.nCopies(6, 0.0));
        List<String> trafficLabels = new ArrayList<>();
        Map<String, Integer> ymToIndex = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        for (int i = 5; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, -i);
            int month = c.get(Calendar.MONTH);
            int year = c.get(Calendar.YEAR);
            String label = EN_MONTHS[month];
            String ym = String.format("%04d-%02d", year, month + 1);
            trafficLabels.add(label);
            ymToIndex.put(ym, 5 - i);
        }

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
        List<String> weeklyTrafficLabels = new ArrayList<>();
        Map<String, Integer> dateToIndex = new HashMap<>();

        Calendar cal7 = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar c = (Calendar) cal7.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            String label = EN_DAYS[dayOfWeek - 1];
            String dateStr = String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            weeklyTrafficLabels.add(label);
            dateToIndex.put(dateStr, 6 - i);
        }

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
        List<String> newCoursesLabels = new ArrayList<>();
        Map<String, Integer> ymToIndex = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        for (int i = 5; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, -i);
            int month = c.get(Calendar.MONTH);
            int year = c.get(Calendar.YEAR);
            String label = EN_MONTHS[month];
            String ym = String.format("%04d-%02d", year, month + 1);
            newCoursesLabels.add(label);
            ymToIndex.put(ym, 5 - i);
        }

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
        List<String> weeklyCoursesLabels = new ArrayList<>();
        Map<String, Integer> dateToIndex = new HashMap<>();

        Calendar cal7 = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar c = (Calendar) cal7.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            String label = EN_DAYS[dayOfWeek - 1];
            String dateStr = String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            weeklyCoursesLabels.add(label);
            dateToIndex.put(dateStr, 6 - i);
        }

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
        List<String> newUsersLabels = new ArrayList<>();
        Map<String, Integer> ymToIndex = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        for (int i = 5; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, -i);
            int month = c.get(Calendar.MONTH);
            int year = c.get(Calendar.YEAR);
            String label = EN_MONTHS[month];
            String ym = String.format("%04d-%02d", year, month + 1);
            newUsersLabels.add(label);
            ymToIndex.put(ym, 5 - i);
        }

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
        List<String> weeklyUsersLabels = new ArrayList<>();
        Map<String, Integer> dateToIndex = new HashMap<>();

        Calendar cal7 = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar c = (Calendar) cal7.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            String label = EN_DAYS[dayOfWeek - 1];
            String dateStr = String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            weeklyUsersLabels.add(label);
            dateToIndex.put(dateStr, 6 - i);
        }

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
        List<String> enrollmentsLabels = new ArrayList<>();
        Map<String, Integer> ymToIndex = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        for (int i = 5; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, -i);
            int month = c.get(Calendar.MONTH);
            int year = c.get(Calendar.YEAR);
            String label = EN_MONTHS[month];
            String ym = String.format("%04d-%02d", year, month + 1);
            enrollmentsLabels.add(label);
            ymToIndex.put(ym, 5 - i);
        }

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
        List<String> weeklyEnrollmentsLabels = new ArrayList<>();
        Map<String, Integer> dateToIndex = new HashMap<>();

        Calendar cal7 = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar c = (Calendar) cal7.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            String label = EN_DAYS[dayOfWeek - 1];
            String dateStr = String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            weeklyEnrollmentsLabels.add(label);
            dateToIndex.put(dateStr, 6 - i);
        }

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
    public List<SystemActivityDto> getRecentActivities() {
        final List<SystemActivityDto> activityList = new ArrayList<>();
        jdbc.query("""
                SELECT c.id, u.full_name, c.title, COALESCE(c.updated_at, c.created_at) AS act_time, c.status
                FROM courses c JOIN users u ON u.id = c.instructor_id
                ORDER BY COALESCE(c.updated_at, c.created_at) DESC LIMIT 3
                """, (rs) -> {
            String status = rs.getString("status");
            String act = "PENDING".equals(status) ? "đã gửi phê duyệt khóa học: " + rs.getString("title")
                    : "APPROVED".equals(status) || "PUBLISHED".equals(status) ? "đã xuất bản khóa học: " + rs.getString("title")
                    : "đã cập nhật khóa học: " + rs.getString("title");
            String type = "PENDING".equals(status) ? "submit" : "PUBLISHED".equals(status) ? "publish" : "add";
            Instant time = rs.getTimestamp("act_time") != null ? rs.getTimestamp("act_time").toInstant() : Instant.now();
            activityList.add(SystemActivityDto.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .who(rs.getString("full_name"))
                    .act(act)
                    .time(DATE_FORMATTER.format(time))
                    .type(type)
                    .build());
        });

        jdbc.query("""
                SELECT s.id, u.full_name, a.title, s.submitted_at, s.status
                FROM assignment_submissions s
                JOIN assignments a ON a.id = s.assignment_id
                JOIN users u ON u.id = s.student_id
                ORDER BY s.submitted_at DESC LIMIT 3
                """, (rs) -> {
            Instant time = rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toInstant() : Instant.now();
            activityList.add(SystemActivityDto.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .who(rs.getString("full_name"))
                    .act("đã nộp bài tập: " + rs.getString("title"))
                    .time(DATE_FORMATTER.format(time))
                    .type("submit")
                    .build());
        });

        return activityList.stream()
                .limit(6)
                .collect(Collectors.toList());
    }

    @Override
    public AdminDashboardResponse getDashboard() {
        AdminDashboardStatsResponse stats = getStats();
        AdminDashboardTrafficResponse traffic = getTrafficChart();
        AdminDashboardCoursesChartResponse courses = getCoursesChart();
        AdminDashboardUsersChartResponse users = getUsersChart();
        AdminDashboardEnrollmentsChartResponse enrollments = getEnrollmentsChart();
        List<PendingApprovalDto> pendingApprovals = getPendingApprovals();
        List<SystemActivityDto> recentActivities = getRecentActivities();

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
                .recentActivities(recentActivities)
                .build();
    }
}
