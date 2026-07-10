package project.lms_rikkei_edu.modules.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.modules.dashboard.dto.response.AdminDashboardResponse;
import project.lms_rikkei_edu.modules.dashboard.dto.response.PendingApprovalDto;
import project.lms_rikkei_edu.modules.dashboard.dto.response.SystemActivityDto;
import project.lms_rikkei_edu.modules.dashboard.service.AdminDashboardService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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
    public AdminDashboardResponse getDashboard() {
        // 1. Total students count
        Integer totalStudentsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'STUDENT'", Integer.class);
        if (totalStudentsCount == null) totalStudentsCount = 0;

        // 2. Total instructors count
        Integer totalInstructorsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'INSTRUCTOR'", Integer.class);
        if (totalInstructorsCount == null) totalInstructorsCount = 0;

        // 3. Active courses count
        Integer activeCoursesCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM courses WHERE status IN ('PUBLISHED', 'APPROVED')", Integer.class);
        if (activeCoursesCount == null) activeCoursesCount = 0;

        // 4. Average completion rate
        Double avgRate = jdbc.queryForObject(
                "SELECT COALESCE(AVG(overall_percentage), 0.0) FROM course_progress", Double.class);
        double averageCompletionRate = avgRate != null ? Math.round(avgRate * 10.0) / 10.0 : 0.0;

        // 5. Traffic data & New courses data (last 6 months rolling window - optimized single queries)
        List<Double> trafficData = new ArrayList<>(Collections.nCopies(6, 0.0));
        List<String> trafficLabels = new ArrayList<>();
        List<Integer> newCoursesData = new ArrayList<>(Collections.nCopies(6, 0));
        List<String> newCoursesLabels = new ArrayList<>();
        Map<String, Integer> ymToIndex = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        for (int i = 5; i >= 0; i--) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, -i);
            int month = c.get(Calendar.MONTH) + 1;
            int year = c.get(Calendar.YEAR);
            String label = "Th" + month;
            String ym = String.format("%04d-%02d", year, month);
            trafficLabels.add(label);
            newCoursesLabels.add(label);
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

        // 5b. Weekly Traffic & Courses data (last 7 days rolling window - exact daily stats)
        List<Double> weeklyTrafficData = new ArrayList<>(Collections.nCopies(7, 0.0));
        List<String> weeklyTrafficLabels = new ArrayList<>();
        List<Integer> weeklyCoursesData = new ArrayList<>(Collections.nCopies(7, 0));
        List<String> weeklyCoursesLabels = new ArrayList<>();
        Map<String, Integer> dateToIndex = new HashMap<>();

        Calendar cal7 = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar c = (Calendar) cal7.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK); // 1 = Sun, 2 = Mon...
            String label = dayOfWeek == Calendar.SUNDAY ? "CN" : "T" + dayOfWeek;
            String dateStr = String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            weeklyTrafficLabels.add(label);
            weeklyCoursesLabels.add(label);
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

        // 6. Pending approvals list
        List<PendingApprovalDto> pendingApprovals = jdbc.query("""
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

        // 7. Recent system activities (dynamic from courses and submissions)
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

        List<SystemActivityDto> recentActivities = activityList.stream()
                .limit(6)
                .collect(Collectors.toList());

        return AdminDashboardResponse.builder()
                .totalStudentsCount(totalStudentsCount)
                .totalInstructorsCount(totalInstructorsCount)
                .activeCoursesCount(activeCoursesCount)
                .averageCompletionRate(averageCompletionRate)
                .trafficData(trafficData)
                .trafficLabels(trafficLabels)
                .newCoursesData(newCoursesData)
                .newCoursesLabels(newCoursesLabels)
                .weeklyTrafficData(weeklyTrafficData)
                .weeklyTrafficLabels(weeklyTrafficLabels)
                .weeklyCoursesData(weeklyCoursesData)
                .weeklyCoursesLabels(weeklyCoursesLabels)
                .pendingApprovals(pendingApprovals)
                .recentActivities(recentActivities)
                .build();
    }
}
