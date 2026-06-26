package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.ai.service.retrieval.ScoredChunk;
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

        /**
         * Captures the actual RowMapper lambda and invokes it against a mocked ResultSet
         * so that the rs.getObject / rs.getString / rs.getDouble lines are executed.
         */
        @Test
        @SuppressWarnings("unchecked")
        void rowMapper_mapsAllColumnsCorrectly_withCourseId() throws Exception {
            UUID chunkId  = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID();

            // Capture the RowMapper passed to jdbc.query(...)
            ArgumentCaptor<RowMapper<ScoredChunk>> mapperCaptor =
                    ArgumentCaptor.forClass(RowMapper.class);
            when(jdbc.query(anyString(), mapperCaptor.capture(), any(Object[].class)))
                    .thenReturn(List.of());

            // Trigger the call so the captor fires
            service.search(courseId, queryEmbedding, 5, 0.7);

            // Now invoke the captured RowMapper with a mocked ResultSet
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(chunkId);
            when(rs.getObject("source_id", UUID.class)).thenReturn(sourceId);
            when(rs.getObject("course_id", UUID.class)).thenReturn(courseId);
            when(rs.getInt("chunk_index")).thenReturn(2);
            when(rs.getString("section_title")).thenReturn("Intro");
            when(rs.getString("chunk_text")).thenReturn("Hello world");
            when(rs.getDouble("similarity")).thenReturn(0.95);

            ScoredChunk result = mapperCaptor.getValue().mapRow(rs, 0);

            assertThat(result.chunkId()).isEqualTo(chunkId);
            assertThat(result.sourceId()).isEqualTo(sourceId);
            assertThat(result.courseId()).isEqualTo(courseId);
            assertThat(result.chunkIndex()).isEqualTo(2);
            assertThat(result.sectionTitle()).isEqualTo("Intro");
            assertThat(result.chunkText()).isEqualTo("Hello world");
            assertThat(result.similarity()).isEqualTo(0.95);
        }

        @Test
        @SuppressWarnings("unchecked")
        void rowMapper_mapsAllColumnsCorrectly_withNullCourseId() throws Exception {
            UUID chunkId  = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID();

            ArgumentCaptor<RowMapper<ScoredChunk>> mapperCaptor =
                    ArgumentCaptor.forClass(RowMapper.class);
            when(jdbc.query(anyString(), mapperCaptor.capture(), any(Object[].class)))
                    .thenReturn(List.of());

            // courseId=null triggers the system-doc SQL branch
            service.search(null, queryEmbedding, 3, 0.5);

            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(chunkId);
            when(rs.getObject("source_id", UUID.class)).thenReturn(sourceId);
            when(rs.getObject("course_id", UUID.class)).thenReturn(null);
            when(rs.getInt("chunk_index")).thenReturn(0);
            when(rs.getString("section_title")).thenReturn(null);
            when(rs.getString("chunk_text")).thenReturn("System doc");
            when(rs.getDouble("similarity")).thenReturn(0.8);

            ScoredChunk result = mapperCaptor.getValue().mapRow(rs, 0);

            assertThat(result.chunkId()).isEqualTo(chunkId);
            assertThat(result.courseId()).isNull();
            assertThat(result.sectionTitle()).isNull();
            assertThat(result.chunkText()).isEqualTo("System doc");
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
