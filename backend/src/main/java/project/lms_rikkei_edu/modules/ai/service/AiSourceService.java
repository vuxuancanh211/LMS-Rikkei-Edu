package project.lms_rikkei_edu.modules.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.ai.dto.request.SourceIngestRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.AvailableResourceResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.service.ingestion.CourseEmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.ingestion.IngestionOrchestrator;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;
import project.lms_rikkei_edu.modules.course.repository.LessonResourceRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiSourceService {

    private final AiSourceRepository   sourceRepo;
    private final DocumentChunkRepository chunkRepo;
    private final IngestionOrchestrator orchestrator;
    private final LessonResourceRepository lessonResourceRepo;
    private final CourseEmbeddingService courseEmbeddingService;

    /** Register a new source and immediately start ingestion. */
    @Transactional
    public SourceResponse ingest(SourceIngestRequest req) {
        Map<String, Object> meta = new HashMap<>();
        if (req.metadata() != null) meta.putAll(req.metadata());
        if (req.content() != null)  meta.put("content", req.content());

        // PDF/DOC handlers read the S3 key from AiSource.externalId, not from metadata directly.
        Object s3Key = meta.get("s3Key");

        AiSource source = AiSource.builder()
                .courseId(req.courseId())
                .uploadedBy(req.uploadedBy())
                .sourceType(req.sourceType())
                .sourceName(req.sourceName())
                .sourceUrl(req.sourceUrl())
                .externalId(s3Key != null ? s3Key.toString() : null)
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

    /** List lesson resources (PDF/DOC) in a course that are eligible to be added to the AI knowledge base. */
    public List<AvailableResourceResponse> listAvailableResources(UUID courseId) {
        Map<UUID, AiSource> byResourceId = sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId).stream()
                .filter(s -> s.getResourceId() != null)
                .collect(Collectors.toMap(AiSource::getResourceId, s -> s, (a, b) -> a));

        return lessonResourceRepo.findAllByCourseIdWithLessonAndChapter(courseId).stream()
                .filter(r -> resolveIngestibleMimeType(r) != null)
                .map(r -> toAvailableResourceResponse(r, byResourceId.get(r.getId())))
                .toList();
    }

    /**
     * The {@code mime_type} column on older/manually-seeded lesson resources can be blank even
     * though {@code resourceType} (PDF/DOC) is set correctly — fall back to a synthetic mime type
     * derived from {@code resourceType} so those resources are still ingestible.
     */
    private static String resolveIngestibleMimeType(LessonResource r) {
        if (SourceType.fromMimeType(r.getMimeType()) != null) return r.getMimeType();
        if (r.getResourceType() == ResourceType.PDF) return "application/pdf";
        if (r.getResourceType() == ResourceType.DOC) return "application/msword";
        return null;
    }

    private static AvailableResourceResponse toAvailableResourceResponse(LessonResource r, AiSource existing) {
        return new AvailableResourceResponse(
                r.getId(),
                r.getLesson().getId(),
                r.getLesson().getTitle(),
                r.getLesson().getChapter().getTitle(),
                r.getDisplayName() != null ? r.getDisplayName() : r.getOriginalFilename(),
                r.getMimeType(),
                existing != null,
                existing != null ? existing.getId() : null
        );
    }

    /** Add already-uploaded lesson resources to the AI knowledge base, reusing {@link CourseEmbeddingService}. */
    @Transactional
    public List<SourceResponse> ingestFromResources(UUID courseId, List<UUID> resourceIds) {
        List<SourceResponse> results = new java.util.ArrayList<>();
        for (UUID resourceId : resourceIds) {
            LessonResource resource = lessonResourceRepo.findById(resourceId)
                    .orElseThrow(() -> new IllegalArgumentException("Resource không tồn tại: " + resourceId));
            if (!courseId.equals(resource.getCourseId())) {
                throw new IllegalArgumentException("Resource không thuộc khóa học: " + resourceId);
            }
            String mimeType = resolveIngestibleMimeType(resource);
            if (mimeType == null) {
                throw new IllegalArgumentException("Định dạng file không hỗ trợ đưa vào AI: " + resourceId);
            }
            courseEmbeddingService.embedResource(
                    courseId, resourceId, resource.getS3Key(), mimeType,
                    resource.getDisplayName() != null ? resource.getDisplayName() : resource.getOriginalFilename());

            AiSource created = sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId).stream()
                    .findFirst()
                    .orElseThrow();
            results.add(SourceResponse.from(created));
        }
        return results;
    }
}
