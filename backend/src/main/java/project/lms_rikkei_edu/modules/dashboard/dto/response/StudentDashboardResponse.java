package project.lms_rikkei_edu.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardResponse {
    private String studentName;
    private StatsDto stats;
    private List<CourseSummaryDto> inProgressCourses;
    private List<DueAssignmentDto> dueAssignments;
    private List<Double> weeklyStudyHours;
    private List<SkillProgressDto> skillProgress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsDto {
        private int activeCoursesCount;
        private int nearCompletionCoursesCount;
        private int pendingTasksCount;
        private int dueSoonTasksCount;
        private int certificatesCount;
        private double weeklyHours;
        private Double weeklyHoursTrend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseSummaryDto {
        private UUID id;
        private String title;
        private String category;
        private String thumbnailUrl;
        private int progress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DueAssignmentDto {
        private UUID id;
        private String title;
        private String type;
        private String deadline;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillProgressDto {
        private String title;
        private int progress;
    }
}
