package project.lms_rikkei_edu.modules.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.ai.dto.request.AddFromResourcesRequest;
import project.lms_rikkei_edu.modules.ai.dto.request.SourceIngestRequest;
import project.lms_rikkei_edu.modules.ai.dto.request.SourcePresignRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.AvailableResourceResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.SourcePresignResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
import project.lms_rikkei_edu.modules.ai.service.AiSourceService;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.user.enums.UserRole;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/sources")
@RequiredArgsConstructor
public class AiSourceController {

    private final AiSourceService sourceService;
    private final CurrentUserProvider currentUserProvider;
    private final CourseRepository courseRepository;
    private final S3Service s3Service;

    private UserPrincipal currentUser() {
        return currentUserProvider.getCurrentUser()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    /** ADMIN manages any course's sources; INSTRUCTOR only their own. */
    private void verifyCourseOwnership(UUID courseId, UserPrincipal user) {
        if (user.getRole() == UserRole.ADMIN) return;
        if (courseId == null || !courseRepository.existsByIdAndInstructorId(courseId, user.getId())) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }

    /** Get a presigned S3 upload URL for a new AI knowledge-base document. */
    @PostMapping("/presign-upload")
    public ResponseEntity<SourcePresignResponse> presignUpload(@Valid @RequestBody SourcePresignRequest req) {
        verifyCourseOwnership(req.courseId(), currentUser());
        String s3Key = "ai-sources/%s/%s-%s".formatted(req.courseId(), UUID.randomUUID(), req.originalFilename());
        String uploadUrl = s3Service.generatePresignedPutUrl(s3Key, req.mimeType(), 3600).url().toString();
        return ResponseEntity.ok(new SourcePresignResponse(uploadUrl, s3Key));
    }

    /** Ingest a new knowledge source and start embedding. */
    @PostMapping
    public ResponseEntity<SourceResponse> ingest(@Valid @RequestBody SourceIngestRequest req) {
        UserPrincipal user = currentUser();
        verifyCourseOwnership(req.courseId(), user);
        SourceIngestRequest safeReq = new SourceIngestRequest(
                req.courseId(), user.getId(), req.sourceType(), req.sourceName(),
                req.content(), req.sourceUrl(), req.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.ingest(safeReq));
    }

    /** List all active sources for a course. */
    @GetMapping
    public ResponseEntity<List<SourceResponse>> list(@RequestParam UUID courseId) {
        verifyCourseOwnership(courseId, currentUser());
        return ResponseEntity.ok(sourceService.listByCourse(courseId));
    }

    /** Get a single source by id. */
    @GetMapping("/{id}")
    public ResponseEntity<SourceResponse> get(@PathVariable UUID id) {
        SourceResponse source = sourceService.getById(id);
        verifyCourseOwnership(source.courseId(), currentUser());
        return ResponseEntity.ok(source);
    }

    /** Soft-delete a source and remove all its chunks. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        SourceResponse source = sourceService.getById(id);
        verifyCourseOwnership(source.courseId(), currentUser());
        sourceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Re-trigger ingestion (clears old chunks first). */
    @PostMapping("/{id}/reingest")
    public ResponseEntity<SourceResponse> reingest(@PathVariable UUID id) {
        SourceResponse source = sourceService.getById(id);
        verifyCourseOwnership(source.courseId(), currentUser());
        return ResponseEntity.ok(sourceService.reingest(id));
    }

    /** List lesson resources (PDF/DOC) in the course eligible to be added to the AI knowledge base. */
    @GetMapping("/available-resources")
    public ResponseEntity<List<AvailableResourceResponse>> availableResources(@RequestParam UUID courseId) {
        verifyCourseOwnership(courseId, currentUser());
        return ResponseEntity.ok(sourceService.listAvailableResources(courseId));
    }

    /** Add already-uploaded lesson resources to the AI knowledge base. */
    @PostMapping("/from-resources")
    public ResponseEntity<List<SourceResponse>> addFromResources(@Valid @RequestBody AddFromResourcesRequest req) {
        verifyCourseOwnership(req.courseId(), currentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.ingestFromResources(req.courseId(), req.resourceIds()));
    }
}
