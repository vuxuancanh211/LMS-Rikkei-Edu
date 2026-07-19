package project.lms_rikkei_edu.modules.quiz.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService;
import project.lms_rikkei_edu.modules.quiz.service.BankQuestionEmbeddingService.IdText;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankQuestionEmbeddingBackfillJobTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private BankQuestionEmbeddingService embeddingService;

    private BankQuestionEmbeddingBackfillJob job;

    @BeforeEach
    void setUp() {
        job = new BankQuestionEmbeddingBackfillJob(jdbc, embeddingService);
    }

    @Test
    void noMissingEmbeddings_isNoOp() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(100))).thenReturn(List.of());

        job.backfillMissingEmbeddings();

        verify(embeddingService, never()).embedAndSaveBatchSafe(any());
    }

    @Test
    void missingEmbeddingsFound_delegatesToEmbeddingService() {
        List<IdText> missing = List.of(new IdText(UUID.randomUUID(), "Q1"));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(100))).thenReturn(missing);

        job.backfillMissingEmbeddings();

        verify(embeddingService).embedAndSaveBatchSafe(missing);
    }

    @Test
    void queryThrows_isSwallowed() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(100)))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.backfillMissingEmbeddings()).doesNotThrowAnyException();

        verify(embeddingService, never()).embedAndSaveBatchSafe(any());
    }
}
