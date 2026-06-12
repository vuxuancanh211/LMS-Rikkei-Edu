package project.lms_rikkei_edu.modules.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.ai.dto.request.SourceIngestRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.service.ingestion.IngestionOrchestrator;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiSourceService {

    private final AiSourceRepository   sourceRepo;
    private final DocumentChunkRepository chunkRepo;
    private final IngestionOrchestrator orchestrator;

    /** Register a new source and immediately start ingestion. */
    @Transactional
    public SourceResponse ingest(SourceIngestRequest req) {
        Map<String, Object> meta = new HashMap<>();
        if (req.metadata() != null) meta.putAll(req.metadata());
        if (req.content() != null)  meta.put("content", req.content());

        AiSource source = AiSource.builder()
                .courseId(req.courseId())
                .uploadedBy(req.uploadedBy())
                .sourceType(req.sourceType())
                .sourceName(req.sourceName())
                .sourceUrl(req.sourceUrl())
                .status("ACTIVE")
                .ingestStatus(IngestStatus.PENDING)
                .metadata(meta)
                .createdAt(OffsetDateTime.now())
                .build();

        source = sourceRepo.save(source);

        // Run synchronously for now; wrap in @Async / queue for production scale.
        orchestrator.ingest(source.getId());

        // Reload to get the updated status after ingestion.
        source = sourceRepo.findById(source.getId()).orElseThrow();
        return SourceResponse.from(source);
    }

    public List<SourceResponse> listByCourse(UUID courseId) {
        return sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId)
                .stream()
                .map(SourceResponse::from)
                .toList();
    }

    public SourceResponse getById(UUID id) {
        return sourceRepo.findById(id)
                .map(SourceResponse::from)
                .orElseThrow(() -> new AiSourceNotFoundException(id));
    }

    /** Soft-delete the source and remove its chunks. */
    @Transactional
    public void delete(UUID id) {
        AiSource source = sourceRepo.findById(id)
                .orElseThrow(() -> new AiSourceNotFoundException(id));
        chunkRepo.deleteBySourceId(id);
        source.setStatus("DELETED");
        source.setDeletedAt(OffsetDateTime.now());
        sourceRepo.save(source);
    }

    /** Delete existing chunks and re-run ingestion. */
    @Transactional
    public SourceResponse reingest(UUID id) {
        orchestrator.reingest(id);
        return sourceRepo.findById(id)
                .map(SourceResponse::from)
                .orElseThrow();
    }
}
