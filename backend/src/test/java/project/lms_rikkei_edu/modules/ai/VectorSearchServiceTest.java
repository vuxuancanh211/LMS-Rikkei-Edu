package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.ai.service.retrieval.ScoredChunk;
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock JdbcTemplate jdbc;

    VectorSearchService service;

    @BeforeEach
    void setUp() {
        service = new VectorSearchService(jdbc);
    }

    // ── saveEmbedding ─────────────────────────────────────────────────────────

    @Nested
    class SaveEmbedding {

        @Test
        void updatesEmbedding_whenChunkExists() {
            UUID chunkId = UUID.randomUUID();
            float[] embedding = {0.1f, 0.2f, 0.3f};

            when(jdbc.update(anyString(), anyString(), eq(chunkId))).thenReturn(1);

            service.saveEmbedding(chunkId, embedding);

            verify(jdbc).update(
                    contains("UPDATE document_chunks SET embedding"),
                    eq(VectorSearchService.toVectorString(embedding)),
                    eq(chunkId)
            );
        }

        @Test
        void logsWarn_whenChunkNotFound() {
            UUID chunkId = UUID.randomUUID();
            when(jdbc.update(anyString(), anyString(), eq(chunkId))).thenReturn(0);

            // Should not throw — just log warning
            service.saveEmbedding(chunkId, new float[]{0.1f});
        }
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Nested
    class Search {

        private final UUID courseId = UUID.randomUUID();
        private final float[] queryEmbedding = {0.1f, 0.2f, 0.3f};

        @Test
        @SuppressWarnings("unchecked")
        void returnsResults_whenCourseIdProvided() {
            ScoredChunk chunk = new ScoredChunk(
                    UUID.randomUUID(), UUID.randomUUID(), courseId, 0,
                    "Section 1", "Sample text", 0.9);


            when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of(chunk));

            List<ScoredChunk> results = service.search(courseId, queryEmbedding, 5, 0.7);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).similarity()).isEqualTo(0.9);
        }

        @Test
        @SuppressWarnings("unchecked")
        void returnsResults_whenCourseIdNull_searchesSystemDocs() {
            ScoredChunk chunk = new ScoredChunk(
                    UUID.randomUUID(), UUID.randomUUID(), null, 0,
                    "Global", "System doc text", 0.85);


            when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of(chunk));

            List<ScoredChunk> results = service.search(null, queryEmbedding, 3, 0.5);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).courseId()).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void returnsEmptyList_whenVectorExtensionUnavailable() {
            when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenThrow(new DataAccessException("pgvector not available") {});

            List<ScoredChunk> results = service.search(courseId, queryEmbedding, 5, 0.7);

            assertThat(results).isEmpty();
        }

        @Test
        @SuppressWarnings("unchecked")
        void returnsEmptyList_whenNoChunksMatch() {
            when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            List<ScoredChunk> results = service.search(courseId, queryEmbedding, 10, 0.9);

            assertThat(results).isEmpty();
        }
    }

    // ── toVectorString ────────────────────────────────────────────────────────

    @Nested
    class ToVectorString {

        @Test
        void formatsCorrectly() {
            float[] v = {0.1f, 0.2f, 0.3f};
            String result = VectorSearchService.toVectorString(v);
            assertThat(result).isEqualTo("[" + 0.1f + "," + 0.2f + "," + 0.3f + "]");
        }

        @Test
        void handlesEmptyArray() {
            String result = VectorSearchService.toVectorString(new float[]{});
            assertThat(result).isEqualTo("[]");
        }

        @Test
        void handlesSingleElement() {
            String result = VectorSearchService.toVectorString(new float[]{1.0f});
            assertThat(result).isEqualTo("[1.0]");
        }
    }
}
