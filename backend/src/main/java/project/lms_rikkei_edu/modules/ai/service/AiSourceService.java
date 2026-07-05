package project.lms_rikkei_edu.modules.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.ai.dto.request.SourceIngestRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.AvailableResourceResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.ChunkResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceViewResponse;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.service.ingestion.CourseEmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.ingestion.IngestionOrchestrator;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonResourceRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final CourseRepository courseRepo;
    private final S3Service s3Service;

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
        return SourceResponse.from(source, courseOf(source.getCourseId()));
    }

    public List<SourceResponse> listByCourse(UUID courseId) {
        Course course = courseOf(courseId);
        return sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId)
                .stream()
                .map(s -> SourceResponse.from(s, course))
                .toList();
    }

    /** List every source in the system — every course's docs plus system-wide (courseId=null) docs. */
    public List<SourceResponse> listAll() {
        List<AiSource> sources = sourceRepo.findByDeletedAtIsNull();
        Map<UUID, Course> courses = coursesById(sources);
        return sources.stream()
                .map(s -> SourceResponse.from(s, courses.get(s.getCourseId())))
                .toList();
    }

    /** List every source across the courses a given instructor owns (system-wide docs are ADMIN-only, excluded here). */
    public List<SourceResponse> listByInstructor(UUID instructorId) {
        List<UUID> courseIds = courseRepo.findAllByInstructorId(instructorId).stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) return List.of();
        List<AiSource> sources = sourceRepo.findByCourseIdInAndDeletedAtIsNull(courseIds);
        Map<UUID, Course> courses = coursesById(sources);
        return sources.stream()
                .map(s -> SourceResponse.from(s, courses.get(s.getCourseId())))
                .toList();
    }

    public SourceResponse getById(UUID id) {
        AiSource source = sourceRepo.findById(id)
                .orElseThrow(() -> new AiSourceNotFoundException(id));
        return SourceResponse.from(source, courseOf(source.getCourseId()));
    }

    private Course courseOf(UUID courseId) {
        return courseId == null ? null : courseRepo.findById(courseId).orElse(null);
    }

    /** Presigned, inline-viewable URL for a source's original uploaded file. PDF/DOC only. */
    public SourceViewResponse getViewUrl(UUID id) {
        AiSource source = sourceRepo.findById(id)
                .orElseThrow(() -> new AiSourceNotFoundException(id));
        if (source.getExternalId() == null) {
            throw new IllegalArgumentException("Tài liệu này không có file gốc để xem");
        }
        String url = s3Service.generatePresignedInlineUrl(source.getExternalId(), 3600).url().toString();
        return new SourceViewResponse(url);
    }

    /** The text chunks actually extracted and embedded for a source — what the AI "read" from it. */
    public List<ChunkResponse> getChunks(UUID id) {
        return chunkRepo.findBySourceIdOrderByChunkIndex(id).stream()
                .map(c -> new ChunkResponse(c.getChunkIndex(), c.getSectionTitle(), c.getChunkText()))
                .toList();
    }

    private Map<UUID, Course> coursesById(List<AiSource> sources) {
        return courseRepo.findAllById(
                        sources.stream().map(AiSource::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
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
        AiSource source = sourceRepo.findById(id).orElseThrow();
        return SourceResponse.from(source, courseOf(source.getCourseId()));
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
        Course course = courseOf(courseId);
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
            results.add(SourceResponse.from(created, course));
        }
        return results;
    }
}
