package project.lms_rikkei_edu.modules.ai.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.ai.entity.AiIngestionJob;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.DocumentChunk;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.JobStatus;
import project.lms_rikkei_edu.modules.ai.repository.AiIngestionJobRepository;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;
import project.lms_rikkei_edu.modules.ai.service.embedding.EmbeddingService;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.exception.IngestionException;
import project.lms_rikkei_edu.modules.ai.service.retrieval.VectorSearchService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the full ingestion pipeline:
 * <pre>
 *   AiSource → handler.extractText() → TextChunker → EmbeddingService → pgvector
 * </pre>
 *
 * <p>New source types are supported by adding a {@link SourceIngestionHandler} Spring bean.
 * This class never needs to be changed when new file formats are added.
 */
@Slf4j
@Service
public class IngestionOrchestrator {

    private final AiSourceRepository       sourceRepo;
    private final AiIngestionJobRepository jobRepo;
    private final DocumentChunkRepository  chunkRepo;
    private final EmbeddingService         embeddingService;
    private final VectorSearchService      vectorSearch;
    private final TextChunker              chunker;

    /** Handler registry: sourceType → handler, built once at startup. */
    private final Map<String, SourceIngestionHandler> handlers;

    public IngestionOrchestrator(
            AiSourceRepository sourceRepo,
            AiIngestionJobRepository jobRepo,
            DocumentChunkRepository chunkRepo,
            EmbeddingService embeddingService,
            VectorSearchService vectorSearch,
            TextChunker chunker,
            List<SourceIngestionHandler> handlerList
    ) {
        this.sourceRepo       = sourceRepo;
        this.jobRepo          = jobRepo;
        this.chunkRepo        = chunkRepo;
        this.embeddingService = embeddingService;
        this.vectorSearch     = vectorSearch;
        this.chunker          = chunker;
        this.handlers         = handlerList.stream()
                .collect(Collectors.toMap(h -> h.supportedType().name(), Function.identity()));
        log.info("Registered ingestion handlers: {}", this.handlers.keySet());
    }

    /**
     * Run the full ingestion pipeline for the given source.
     * Creates an {@link AiIngestionJob} to track progress.
     *
     * @param sourceId UUID of an existing, non-deleted {@link AiSource}
     * @throws IllegalArgumentException if the source is not found or has no handler
     */
    @Transactional
    public void ingest(UUID sourceId) {
        AiSource source = sourceRepo.findById(sourceId)
                .orElseThrow(() -> new AiSourceNotFoundException(sourceId));

        AiIngestionJob job = createJob(source);

        try {
            source.setIngestStatus(IngestStatus.PROCESSING);
            sourceRepo.save(source);

            SourceIngestionHandler handler = resolveHandler(source);
            List<String> segments = handler.extractText(source);

            if (segments.isEmpty()) {
                throw new IngestionException(sourceId, "No text could be extracted from the source");
            }

            // Chunk all segments together
            List<String> allChunks = segments.stream()
                    .flatMap(seg -> chunker.chunk(seg).stream())
                    .toList();

            log.info("Ingesting sourceId={} → {} segments → {} chunks", sourceId, segments.size(), allChunks.size());

            // Embed in batch (one API call)
            List<float[]> embeddings = embeddingService.embedBatch(allChunks);

            // Persist chunks + embeddings
            for (int i = 0; i < allChunks.size(); i++) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .sourceId(sourceId)
                        .courseId(source.getCourseId())
                        .chunkIndex(i)
                        .chunkText(allChunks.get(i))
                        .createdAt(OffsetDateTime.now())
                        .build();
                chunkRepo.saveAndFlush(chunk);
                vectorSearch.saveEmbedding(chunk.getId(), embeddings.get(i));
            }

            // Mark success
            source.setIngestStatus(IngestStatus.INDEXED);
            source.setChunkCount(allChunks.size());
            source.setIndexedAt(OffsetDateTime.now());
            source.setErrorMessage(null);
            sourceRepo.save(source);

            completeJob(job, JobStatus.COMPLETED, null);
            log.info("Ingestion complete: sourceId={}, chunks={}", sourceId, allChunks.size());

        } catch (Exception ex) {
            log.error("Ingestion failed for sourceId={}: {}", sourceId, ex.getMessage(), ex);

            source.setIngestStatus(IngestStatus.FAILED);
            source.setErrorMessage(truncate(ex.getMessage(), 500));
            sourceRepo.save(source);

            completeJob(job, JobStatus.FAILED, ex.getMessage());
        }
    }

    /**
     * Delete all chunks for a source and reset its ingest status to PENDING,
     * then re-run ingestion. Useful after updating source content.
     */
    @Transactional
    public void reingest(UUID sourceId) {
        chunkRepo.deleteBySourceId(sourceId);
        AiSource source = sourceRepo.findById(sourceId)
                .orElseThrow(() -> new AiSourceNotFoundException(sourceId));
        source.setIngestStatus(IngestStatus.PENDING);
        source.setChunkCount(null);
        sourceRepo.save(source);
        ingest(sourceId);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private SourceIngestionHandler resolveHandler(AiSource source) {
        String key = source.getSourceType() == null ? null : source.getSourceType().name();
        SourceIngestionHandler h = handlers.get(key);
        if (h == null) {
            throw new UnsupportedOperationException(
                    "No ingestion handler for source type: " + key +
                    ". Register a SourceIngestionHandler bean with supportedType()=" + key);
        }
        return h;
    }

    private AiIngestionJob createJob(AiSource source) {
        AiIngestionJob job = AiIngestionJob.builder()
                .sourceId(source.getId())
                .jobType("INGEST")
                .status(JobStatus.PROCESSING)
                .retryCount(0)
                .startedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
        return jobRepo.save(job);
    }

    private void completeJob(AiIngestionJob job, JobStatus status, String error) {
        job.setStatus(status);
        job.setCompletedAt(OffsetDateTime.now());
        job.setErrorMessage(truncate(error, 500));
        jobRepo.save(job);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
