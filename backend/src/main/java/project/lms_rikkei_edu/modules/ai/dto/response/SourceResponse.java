package project.lms_rikkei_edu.modules.ai.dto.response;

import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.course.entity.Course;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceResponse(
        UUID id,
        UUID courseId,
        /** Null when courseId is null (system-wide document) or the course lookup wasn't resolved. */
        String courseName,
        /** Owning instructor of courseId's course. Null for system-wide documents. */
        UUID instructorId,
        SourceType sourceType,
        String sourceName,
        IngestStatus ingestStatus,
        Integer chunkCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime indexedAt,
        /** Non-null when this source was added from an existing lesson resource, rather than uploaded standalone. */
        UUID resourceId
) {
    /** @param course the resolved course for source.getCourseId(), or null (system-wide doc / lookup miss). */
    public static SourceResponse from(AiSource source, Course course) {
        return new SourceResponse(
                source.getId(),
                source.getCourseId(),
                course != null ? course.getTitle() : null,
                course != null ? course.getInstructorId() : null,
                source.getSourceType(),
                source.getSourceName(),
                source.getIngestStatus(),
                source.getChunkCount(),
                source.getErrorMessage(),
                source.getCreatedAt(),
                source.getIndexedAt(),
                source.getResourceId()
        );
    }
}
