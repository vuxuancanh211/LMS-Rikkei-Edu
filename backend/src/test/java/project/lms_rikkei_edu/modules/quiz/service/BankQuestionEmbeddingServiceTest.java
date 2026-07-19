package project.lms_rikkei_edu.modules.quiz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionDifficulty;
import project.lms_rikkei_edu.modules.quiz.enums.QuestionStatus;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService.IdText;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService.SemanticHit;
import project.lms_rikkei_edu.modules.quiz.service.impl.BankQuestionEmbeddingServiceImpl;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BankQuestionEmbeddingServiceTest {

    @Mock private EmbeddingService embeddingService;
    @Mock private JdbcTemplate jdbc;

    private BankQuestionEmbeddingService service;

    private final UUID courseId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BankQuestionEmbeddingServiceImpl(embeddingService, jdbc);
    }

    @Nested
    class EmbedAndSaveSafe {

        @Test
        void happyPath_upsertsEmbedding() {
            when(embeddingService.embed("What is 1+1?")).thenReturn(new float[]{0.1f, 0.2f});

            service.embedAndSaveSafe(questionId, "What is 1+1?");

            verify(jdbc).update(anyString(), eq(questionId), eq("[0.1,0.2]"));
        }

        @Test
        void embedThrows_isSwallowed() {
            when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("embedding down"));

            service.embedAndSaveSafe(questionId, "text");

            verify(jdbc, never()).update(anyString(), any(UUID.class), anyString());
        }

        @Test
        void jdbcUpdateThrows_isSwallowed() {
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(jdbc.update(anyString(), any(UUID.class), anyString())).thenThrow(new RuntimeException("db down"));

            org.assertj.core.api.Assertions.assertThatCode(() -> service.embedAndSaveSafe(questionId, "text"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class EmbedAndSaveBatchSafe {

        @Test
        void nullItems_isNoOp() {
            service.embedAndSaveBatchSafe(null);

            verifyNoInteractions(embeddingService, jdbc);
        }

        @Test
        void emptyItems_isNoOp() {
            service.embedAndSaveBatchSafe(List.of());

            verifyNoInteractions(embeddingService, jdbc);
        }

        @Test
        void happyPath_batchUpsertsAllItems() throws Exception {
            UUID id2 = UUID.randomUUID();
            List<IdText> items = List.of(new IdText(questionId, "Q1"), new IdText(id2, "Q2"));
            when(embeddingService.embedBatch(List.of("Q1", "Q2")))
                    .thenReturn(List.of(new float[]{0.1f}, new float[]{0.2f}));

            service.embedAndSaveBatchSafe(items);

            ArgumentCaptor<BatchPreparedStatementSetter> captor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
            verify(jdbc).batchUpdate(anyString(), captor.capture());
            BatchPreparedStatementSetter setter = captor.getValue();
            assertThat(setter.getBatchSize()).isEqualTo(2);

            PreparedStatement ps = mock(PreparedStatement.class);
            setter.setValues(ps, 0);
            verify(ps).setObject(1, questionId);
            verify(ps).setString(2, "[0.1]");
            setter.setValues(ps, 1);
            verify(ps).setObject(1, id2);
            verify(ps).setString(2, "[0.2]");
        }

        @Test
        void embedBatchThrows_isSwallowed() {
            List<IdText> items = List.of(new IdText(questionId, "Q1"));
            when(embeddingService.embedBatch(anyList())).thenThrow(new RuntimeException("embedding down"));

            service.embedAndSaveBatchSafe(items);

            verify(jdbc, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        }
    }

    @Nested
    class SearchSimilar {

        @Test
        void embedThrows_returnsEmptyList() {
            when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("embedding down"));

            List<SemanticHit> result = service.searchSimilar(
                    courseId, "index", null, null, null, null, 10, 0.4);

            assertThat(result).isEmpty();
            verifyNoInteractions(jdbc);
        }

        @Test
        void noFilters_returnsHits() {
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            SemanticHit hit = new SemanticHit(questionId, 0.9);
            when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of(hit));

            List<SemanticHit> result = service.searchSimilar(
                    courseId, "index", null, null, null, null, 10, 0.4);

            assertThat(result).containsExactly(hit);
        }

        @Test
        void allFilters_appendsAllClausesToSql() {
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            service.searchSimilar(courseId, "index", QuestionStatus.ACTIVE, QuestionDifficulty.EASY,
                    "Index", List.of(UUID.randomUUID(), UUID.randomUUID()), 5, 0.5);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbc).query(sqlCaptor.capture(), any(org.springframework.jdbc.core.RowMapper.class), paramsCaptor.capture());

            assertThat(sqlCaptor.getValue())
                    .contains("bq.status = ?")
                    .contains("bq.difficulty = ?")
                    .contains("bq.subject_tag = ?")
                    .contains("bq.id NOT IN (?,?)");
            // vecStr, courseId, vecStr, threshold, status, difficulty, subjectTag, excludeId1, excludeId2, vecStr, topK
            assertThat(paramsCaptor.getValue()).hasSize(11);
        }

        @Test
        void queryThrows_returnsEmptyList() {
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenThrow(new RuntimeException("query failed"));

            List<SemanticHit> result = service.searchSimilar(
                    courseId, "index", null, null, null, null, 10, 0.4);

            assertThat(result).isEmpty();
        }
    }
}
