package project.lms_rikkei_edu.modules.ai.dto.response;

import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceResponse(
        UUID id,
        UUID courseId,
        SourceType sourceType,
        String sourceName,
        IngestStatus ingestStatus,
        Integer chunkCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime indexedAt
) {
    public static SourceResponse from(AiSource source) {
        return new SourceResponse(
                source.getId(),
                source.getCourseId(),
                source.getSourceType(),
                source.getSourceName(),
                source.getIngestStatus(),
                source.getChunkCount(),
                source.getErrorMessage(),
                source.getCreatedAt(),
                source.getIndexedAt()
        );
    }
}
