package project.lms_rikkei_edu.modules.ai.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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
import project.lms_rikkei_edu.modules.notification.enums.NotificationType;
import project.lms_rikkei_edu.modules.notification.service.NotificationPreferenceService;
import project.lms_rikkei_edu.modules.notification.service.NotificationService;

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
    private final NotificationService              notificationService;
    private final NotificationPreferenceService     notificationPreferenceService;

    /** Handler registry: sourceType → handler, built once at startup. */
    private final Map<String, SourceIngestionHandler> handlers;

    public IngestionOrchestrator(
            AiSourceRepository sourceRepo,
            AiIngestionJobRepository jobRepo,
            DocumentChunkRepository chunkRepo,
            EmbeddingService embeddingService,
            VectorSearchService vectorSearch,
            TextChunker chunker,
            NotificationService notificationService,
            NotificationPreferenceService notificationPreferenceService,
            List<SourceIngestionHandler> handlerList
    ) {
        this.sourceRepo       = sourceRepo;
        this.jobRepo          = jobRepo;
        this.chunkRepo        = chunkRepo;
        this.embeddingService = embeddingService;
        this.vectorSearch     = vectorSearch;
        this.chunker          = chunker;
        this.notificationService = notificationService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.handlers         = handlerList.stream()
                .collect(Collectors.toMap(h -> h.supportedType().name(), Function.identity()));
        log.info("Registered ingestion handlers: {}", this.handlers.keySet());
    }

    /**
     * Run the full ingestion pipeline for the given source, synchronously on the calling thread.
     * Creates an {@link AiIngestionJob} to track progress.
     *
     * <p>Intentionally NOT {@code @Transactional}: this method spans a slow external OpenAI
     * embeddings call (up to ~45s), and holding a pooled DB connection open for that whole
     * duration is wasteful. Each individual repository call below is already transactional on
     * its own (Spring Data JPA default), so atomicity per-call is preserved; we just don't need
     * one giant transaction wrapping the external HTTP call too.
     *
     * @param sourceId UUID of an existing, non-deleted {@link AiSource}
     * @throws IllegalArgumentException if the source is not found or has no handler
     */
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
            notifyResult(source, job, true);

        } catch (Exception ex) {
            log.error("Ingestion failed for sourceId={}: {}", sourceId, ex.getMessage(), ex);

            // Clean up any chunks partially saved before the failure — avoid orphaned rows
            // for a source that's marked FAILED (e.g. error mid-loop after chunk 0..k already saved).
            chunkRepo.deleteBySourceId(sourceId);

            source.setIngestStatus(IngestStatus.FAILED);
            source.setChunkCount(null);
            source.setErrorMessage(truncate(ex.getMessage(), 500));
            sourceRepo.save(source);

            completeJob(job, JobStatus.FAILED, ex.getMessage());
            notifyResult(source, job, false);
        }
    }

    /** Fire-and-forget entry point — runs {@link #ingest(UUID)} on the async task executor. */
    @Async
    public void ingestAsync(UUID sourceId) {
        ingest(sourceId);
    }

    /**
     * Delete all chunks for a source and reset its ingest status to PENDING.
     * Fast, synchronous — call {@link #ingest} or {@link #ingestAsync} separately afterward
     * to actually re-run the pipeline. Split out so callers can return a response reflecting
     * the PENDING status immediately, before the (possibly async) ingestion runs.
     */
    public void resetForReingest(UUID sourceId) {
        chunkRepo.deleteBySourceId(sourceId);
        AiSource source = sourceRepo.findById(sourceId)
                .orElseThrow(() -> new AiSourceNotFoundException(sourceId));
        source.setIngestStatus(IngestStatus.PENDING);
        source.setChunkCount(null);
        source.setErrorMessage(null);
        sourceRepo.save(source);
    }

    /** Notify the uploader that their document finished processing (success or failure). */
    private void notifyResult(AiSource source, AiIngestionJob job, boolean success) {
        UUID recipientId = source.getUploadedBy();
        if (recipientId == null) return; // e.g. ingested from an existing lesson resource, no uploader tracked

        NotificationType type = success ? NotificationType.AI_SOURCE_INDEXED : NotificationType.AI_SOURCE_FAILED;
        if (!notificationPreferenceService.isInAppEnabled(recipientId, type.name())) return;

        String title = success ? "Tài liệu AI đã xử lý xong" : "Xử lý tài liệu AI thất bại";
        String body = success
                ? "\"%s\" đã được index thành công (%d đoạn nội dung).".formatted(source.getSourceName(), source.getChunkCount())
                : "\"%s\" xử lý thất bại: %s".formatted(source.getSourceName(), truncate(source.getErrorMessage(), 200));

        try {
            notificationService.createNotification(
                    recipientId, type.name(), title, body,
                    "AI_SOURCE", source.getId(), null, null,
                    "ai-ingest-" + job.getId());
        } catch (Exception ex) {
            log.warn("Failed to create ingestion notification for sourceId={}: {}", source.getId(), ex.getMessage());
        }
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
