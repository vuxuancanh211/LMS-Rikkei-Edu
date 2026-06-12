package project.lms_rikkei_edu.modules.ai.service.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * All pgvector operations — embedding persistence and similarity search.
 *
 * <p>JPA cannot map PostgreSQL's {@code vector} type natively, so this service
 * uses {@link JdbcTemplate} with the {@code ::vector} cast for all vector-related
 * SQL. Regular CRUD on {@code document_chunks} still goes through the JPA repository.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final JdbcTemplate jdbc;

    /**
     * Persist an embedding for an already-saved {@link project.lms_rikkei_edu.modules.ai.entity.DocumentChunk}.
     *
     * @param chunkId   the chunk's UUID (must already exist in the table)
     * @param embedding float array of length matching the {@code vector(N)} column
     */
    public void saveEmbedding(UUID chunkId, float[] embedding) {
        String vecStr = toVectorString(embedding);
        int updated = jdbc.update(
                "UPDATE document_chunks SET embedding = ?::vector WHERE id = ?",
                vecStr, chunkId
        );
        if (updated == 0) {
            log.warn("saveEmbedding: no row found for chunkId={}", chunkId);
        }
    }

    /**
     * Cosine-similarity search over all indexed chunks for a course.
     *
     * @param courseId        filter results to this course
     * @param queryEmbedding  embedding of the user's question
     * @param topK            maximum number of results to return
     * @param minSimilarity   minimum 1 - cosine_distance threshold (0 – 1)
     * @return chunks ordered by descending similarity
     */
    public List<ScoredChunk> search(UUID courseId, float[] queryEmbedding, int topK, double minSimilarity) {
        String vecStr = toVectorString(queryEmbedding);

        String sql = """
                SELECT dc.id,
                       dc.source_id,
                       dc.course_id,
                       dc.chunk_index,
                       dc.section_title,
                       dc.chunk_text,
                       1 - (dc.embedding <=> ?::vector) AS similarity
                FROM document_chunks dc
                WHERE dc.course_id = ?
                  AND dc.embedding IS NOT NULL
                  AND 1 - (dc.embedding <=> ?::vector) >= ?
                ORDER BY dc.embedding <=> ?::vector
                LIMIT ?
                """;

        try {
            return jdbc.query(sql,
                    (rs, i) -> new ScoredChunk(
                            rs.getObject("id", UUID.class),
                            rs.getObject("source_id", UUID.class),
                            rs.getObject("course_id", UUID.class),
                            rs.getInt("chunk_index"),
                            rs.getString("section_title"),
                            rs.getString("chunk_text"),
                            rs.getDouble("similarity")
                    ),
                    vecStr, courseId, vecStr, minSimilarity, vecStr, topK
            );
        } catch (DataAccessException ex) {
            // embedding column is TEXT instead of vector(N) — pgvector extension not enabled
            // or table not yet migrated. Return empty so the chat still works without RAG context.
            log.warn("Vector search unavailable for courseId={}: {}. " +
                     "Run: ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(1024) USING embedding::vector",
                     courseId, ex.getMostSpecificCause().getMessage());
            return List.of();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Convert {@code float[]} to pgvector literal, e.g. {@code "[0.1,0.2,0.3]"}. */
    public static String toVectorString(float[] embedding) {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (float v : embedding) sj.add(Float.toString(v));
        return sj.toString();
    }
}
