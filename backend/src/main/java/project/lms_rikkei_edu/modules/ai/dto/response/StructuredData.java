package project.lms_rikkei_edu.modules.ai.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Optional structured payload attached to a {@link ChatResponse} alongside the
 * free-text answer, so the frontend can render a real table/card instead of
 * relying on the LLM's prose. Always built from data already loaded from the
 * DB (never parsed back out of the LLM's answer), so it can't be wrong the
 * way asking the LLM to "also compute this in JSON" could be.
 */
public record StructuredData(String type, List<CourseListItem> items) {

    /** {@code type = "COURSE_LIST"}: an instructor's own courses. */
    public record CourseListItem(UUID courseId, String title, String status, Double metric) {}
}
