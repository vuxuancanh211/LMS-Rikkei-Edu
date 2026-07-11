package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import project.lms_rikkei_edu.modules.ai.entity.AiIngestionJob;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.DocumentChunk;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.JobStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.repository.AiIngestionJobRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.ingestion.IngestionOrchestrator;
import project.lms_rikkei_edu.modules.ai.service.ingestion.SourceIngestionHandler;
import project.lms_rikkei_edu.modules.ai.service.ingestion.TextChunker;
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestionOrchestratorTest {

    @Mock AiSourceRepository sourceRepo;
    @Mock AiIngestionJobRepository jobRepo;
    @Mock DocumentChunkRepository chunkRepo;
    @Mock EmbeddingService embeddingService;
    @Mock VectorSearchService vectorSearch;
    @Mock NotificationService notificationService;
    @Mock NotificationPreferenceService notificationPreferenceService;

    IngestionOrchestrator orchestrator;
    TextChunker chunker = new TextChunker();

    private final UUID sourceId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    // Stub TEXT handler
    private SourceIngestionHandler textHandler;

    @BeforeEach
    void setUp() {
        textHandler = new SourceIngestionHandler() {
            @Override public SourceType supportedType() { return SourceType.TEXT; }
            @Override public List<String> extractText(AiSource source) {
                return List.of("Hello world content for embedding.");
            }
        };

        orchestrator = new IngestionOrchestrator(
                sourceRepo, jobRepo, chunkRepo, embeddingService, vectorSearch, chunker,
                notificationService, notificationPreferenceService,
                List.of(textHandler));

        AiIngestionJob job = AiIngestionJob.builder()
                .id(UUID.randomUUID())
                .sourceId(sourceId)
                .status(JobStatus.PROCESSING)
                .retryCount(0)
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
        when(jobRepo.save(any())).thenReturn(job);
    }

    private AiSource buildSource() {
        return AiSource.builder()
                .id(sourceId)
                .courseId(courseId)
                .sourceType(SourceType.TEXT)
                .sourceName("Test Source")
                .status("ACTIVE")
                .ingestStatus(IngestStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // ── ingest: happy path ────────────────────────────────────────────────────

    @Nested
    class Ingest {

        @Test
        void ingestsSuccessfully() {
            AiSource source = buildSource();
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));

            DocumentChunk savedChunk = DocumentChunk.builder()
                    .id(UUID.randomUUID())
                    .sourceId(sourceId)
                    .chunkIndex(0)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(chunkRepo.saveAndFlush(any())).thenReturn(savedChunk);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
            when(sourceRepo.save(any())).thenReturn(source);

            orchestrator.ingest(sourceId);

            verify(embeddingService).embedBatch(anyList());
            verify(chunkRepo, atLeastOnce()).saveAndFlush(any(DocumentChunk.class));
            verify(vectorSearch, atLeastOnce()).saveEmbedding(any(), any());
            assertThat(source.getIngestStatus()).isEqualTo(IngestStatus.INDEXED);
        }

        @Test
        void throws_whenSourceNotFound() {
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrator.ingest(sourceId))
                    .isInstanceOf(AiSourceNotFoundException.class);
        }

        @Test
        void marksSourceAsFailed_whenHandlerThrows() {
            AiSource source = buildSource();
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));

            SourceIngestionHandler failingHandler = new SourceIngestionHandler() {
                @Override public SourceType supportedType() { return SourceType.TEXT; }
                @Override public List<String> extractText(AiSource s) {
                    throw new RuntimeException("S3 connection error");
                }
            };

            IngestionOrchestrator failOrchestrator = new IngestionOrchestrator(
                    sourceRepo, jobRepo, chunkRepo, embeddingService, vectorSearch, chunker,
                    notificationService, notificationPreferenceService,
                    List.of(failingHandler));
            when(sourceRepo.save(any())).thenReturn(source);

            failOrchestrator.ingest(sourceId);

            assertThat(source.getIngestStatus()).isEqualTo(IngestStatus.FAILED);
            assertThat(source.getErrorMessage()).contains("S3 connection error");
        }

        @Test
        void marksSourceAsFailed_whenNoTextExtracted() {
            AiSource source = buildSource();
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));

            SourceIngestionHandler emptyHandler = new SourceIngestionHandler() {
                @Override public SourceType supportedType() { return SourceType.TEXT; }
                @Override public List<String> extractText(AiSource s) { return List.of(); }
            };

            IngestionOrchestrator emptyOrchestrator = new IngestionOrchestrator(
                    sourceRepo, jobRepo, chunkRepo, embeddingService, vectorSearch, chunker,
                    notificationService, notificationPreferenceService,
                    List.of(emptyHandler));
            when(sourceRepo.save(any())).thenReturn(source);

            emptyOrchestrator.ingest(sourceId);

            assertThat(source.getIngestStatus()).isEqualTo(IngestStatus.FAILED);
        }

        @Test
        void throws_whenNoHandlerForSourceType() {
            AiSource source = buildSource();
            source.setSourceType(SourceType.PDF); // no PDF handler registered
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));
            when(sourceRepo.save(any())).thenReturn(source);

            // Should not throw — failure is caught and saved as FAILED
            orchestrator.ingest(sourceId);

            assertThat(source.getIngestStatus()).isEqualTo(IngestStatus.FAILED);
        }
    }

    // ── reingest ──────────────────────────────────────────────────────────────

    @Nested
    class Reingest {

        @Test
        void deletesChunksAndReingests() {
            AiSource source = buildSource();
            source.setIngestStatus(IngestStatus.INDEXED);
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));

            DocumentChunk savedChunk = DocumentChunk.builder()
                    .id(UUID.randomUUID())
                    .sourceId(sourceId)
                    .chunkIndex(0)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(chunkRepo.saveAndFlush(any())).thenReturn(savedChunk);
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
            when(sourceRepo.save(any())).thenReturn(source);

            orchestrator.resetForReingest(sourceId);
            orchestrator.ingest(sourceId);

            verify(chunkRepo).deleteBySourceId(sourceId);
        }

        @Test
        void throws_whenSourceNotFoundOnReingest() {
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrator.resetForReingest(sourceId))
                    .isInstanceOf(AiSourceNotFoundException.class);
        }
    }
}
