package project.lms_rikkei_edu.modules.ai.service.context;

import java.util.List;
import java.util.UUID;

/**
 * Resolved context for the calling user, used to personalise the AI system prompt.
 */
public record UserContext(
        UUID userId,
        String fullName,
        UserRole role,

        /** STUDENT: enrolled courses + progress. INSTRUCTOR: owned courses + enrollment count. */
        List<CourseInfo> courses,

        /** STUDENT: upcoming deadlines ≤ 7 days. INSTRUCTOR: open assignments ≤ 14 days. */
        List<DeadlineInfo> upcomingDeadlines,

        /** STUDENT: groups the student belongs to. INSTRUCTOR: groups they manage. */
        List<GroupInfo> groups,

        // ── A1: recent lessons (STUDENT only) ────────────────────────────────
        /** Last 3 lessons the student accessed, most recent first. */
        List<RecentLessonInfo> recentLessons,

        // ── A2: recent quiz results (STUDENT only) ────────────────────────────
        /** Most recent quiz attempts (up to 3). */
        List<QuizResultInfo> recentQuizResults,

        // ── A3: unsubmitted assignments (STUDENT only) ────────────────────────
        /** Published assignments the student has NOT yet submitted. */
        List<UnsubmittedAssignmentInfo> unsubmittedAssignments,

        // ── A4: submission gap per assignment (INSTRUCTOR only) ───────────────
        /** For each open assignment: how many enrolled students have NOT submitted. */
        List<SubmissionGapInfo> submissionGaps,

        // ── A5: at-risk students (INSTRUCTOR only) ────────────────────────────
        /** Students with progress < 20% who enrolled > 7 days ago. */
        List<AtRiskStudentInfo> atRiskStudents,

        // ── A6: system stats (ADMIN only) ─────────────────────────────────────
        AdminStats adminStats
) {

    public enum UserRole { STUDENT, INSTRUCTOR, ADMIN }

    // ── existing records ──────────────────────────────────────────────────────

    public record CourseInfo(UUID courseId, String title, String progressStatus, Double progressPct) {}

    public record DeadlineInfo(String assignmentTitle, String courseName, String deadline, boolean isLate) {}

    public record GroupInfo(UUID groupId, String groupName, String courseName) {}

    // ── A1 ───────────────────────────────────────────────────────────────────

    public record RecentLessonInfo(
            String lessonTitle,
            String chapterTitle,
            String courseName,
            String status,           // COMPLETED / IN_PROGRESS
            String lastAccessedAt    // formatted dd/MM/yyyy HH:mm
    ) {}

    // ── A2 ───────────────────────────────────────────────────────────────────

    public record QuizResultInfo(
            String quizTitle,
            String courseName,
            Double score,
            Double maxScore,
            boolean isPassed,
            String submittedAt       // formatted dd/MM/yyyy HH:mm
    ) {}

    // ── A3 ───────────────────────────────────────────────────────────────────

    public record UnsubmittedAssignmentInfo(
            String assignmentTitle,
            String courseName,
            String deadline,         // formatted dd/MM/yyyy HH:mm
            boolean isOverdue
    ) {}

    // ── A4 ───────────────────────────────────────────────────────────────────

    public record SubmissionGapInfo(
            String assignmentTitle,
            String courseName,
            String deadline,
            int totalEnrolled,
            int submitted,
            int notSubmitted
    ) {}

    // ── A5 ───────────────────────────────────────────────────────────────────

    public record AtRiskStudentInfo(
            String studentName,
            String courseName,
            Double progressPct,
            int daysEnrolled
    ) {}

    // ── A6 ───────────────────────────────────────────────────────────────────

    public record AdminStats(
            long totalUsers,
            long totalStudents,
            long totalInstructors,
            long totalCourses,
            long publishedCourses,
            long totalEnrollments,
            long activeConversations
    ) {}
}
