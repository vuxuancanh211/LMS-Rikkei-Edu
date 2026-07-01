package project.lms_rikkei_edu.modules.ai.dto.request;

import jakarta.validation.constraints.NotNull;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;

import java.util.Map;
import java.util.UUID;

/**
 * Request body for ingesting a new knowledge source into the RAG pipeline.
 *
 * <p>For {@code TEXT} sources supply {@code content}.
 * For {@code URL} sources supply {@code sourceUrl}.
 * For {@code PDF} / {@code DOC} sources supply {@code s3Key} inside {@code metadata}.
 */
public record SourceIngestRequest(

        @NotNull UUID courseId,
        @NotNull UUID uploadedBy,
        @NotNull SourceType sourceType,

        /** Human-readable label shown in the admin UI. */
        String sourceName,

        /** Plain text content — used when sourceType is TEXT. */
        String content,

        /** URL to fetch — used when sourceType is URL. */
        String sourceUrl,

        /**
         * Arbitrary key-value pairs forwarded to the ingestion handler.
         * For PDF/DOC: {@code {"s3Key": "courses/123/doc.pdf"}}.
         * For TEXT: content can alternatively be supplied here as {@code {"content": "..."}}.
         */
        Map<String, Object> metadata
) {}
