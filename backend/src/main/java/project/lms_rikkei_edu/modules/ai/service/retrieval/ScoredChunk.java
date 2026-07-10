package project.lms_rikkei_edu.modules.ai.service.retrieval;

import java.util.UUID;

/** A document chunk paired with its cosine-similarity score against a query embedding. */
public record ScoredChunk(
        UUID chunkId,
        UUID sourceId,
        UUID courseId,
        int chunkIndex,
        String sectionTitle,
        String chunkText,
        double similarity
) {}
