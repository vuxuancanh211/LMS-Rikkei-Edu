package project.lms_rikkei_edu.modules.ai.dto.response;

import java.util.UUID;

/** A document chunk that was used as RAG context to generate an answer. */
public record SourceReference(
        UUID chunkId,
        String sourceName,
        String sectionTitle,
        String excerpt,
        double similarity
) {}
