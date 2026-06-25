package project.lms_rikkei_edu.modules.course.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.entity.CourseCategory;
import project.lms_rikkei_edu.modules.course.repository.CourseCategoryRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.service.CourseService;
import project.lms_rikkei_edu.modules.course.service.LessonResourceService;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instructor/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final LessonResourceService lessonResourceService;
    private final CurrentUserProvider currentUserProvider;
    private final S3Service s3Service;
    private final CourseCategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }

    // ── Course ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(
            @Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.createCourse(currentUserId(), request));
    }

    @PostMapping("/presign-thumbnail")
    public ResponseEntity<ThumbnailPresignResponse> presignThumbnail(
            @RequestParam String mimeType) {
        currentUserId(); // auth check
        String ext = mimeType.contains("png") ? "png" : "jpg";
        String s3Key = "courses/thumbnails/" + UUID.randomUUID() + "." + ext;
        String uploadUrl = s3Service.generatePresignedPutUrl(s3Key, mimeType, 3600).url().toString();
        String viewUrl = s3Service.generatePresignedGetUrl(s3Key, 7 * 24 * 3600).url().toString();
        return ResponseEntity.ok(ThumbnailPresignResponse.builder()
                .uploadUrl(uploadUrl).s3Key(s3Key).viewUrl(viewUrl).build());
    }

    @GetMapping
    public ResponseEntity<Page<CourseResponse>> listCourses(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(courseService.listCourses(currentUserId(), pageable));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getCourseDetail(
            @PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.getCourseDetail(currentUserId(), courseId));
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        return ResponseEntity.ok(courseService.updateCourse(currentUserId(), courseId, request));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> deleteCourse(@PathVariable UUID courseId) {
        courseService.deleteCourse(currentUserId(), courseId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{courseId}/submit")
    public ResponseEntity<CourseDetailResponse> submitForApproval(
            @PathVariable UUID courseId,
            @RequestBody(required = false) SubmitUpdateRequest request) {
        String changeSummary = request != null ? request.getChangeSummary() : null;
        return ResponseEntity.ok(courseService.submitForApproval(currentUserId(), courseId, changeSummary));
    }

    @PutMapping("/{courseId}/withdraw")
    public ResponseEntity<CourseDetailResponse> withdrawFromReview(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.withdrawFromReview(currentUserId(), courseId));
    }

    // ── Chapter ───────────────────────────────────────────────────────────────

    @PostMapping("/{courseId}/chapters")
    public ResponseEntity<ChapterResponse> addChapter(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateChapterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addChapter(currentUserId(), courseId, request));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}")
    public ResponseEntity<ChapterResponse> updateChapter(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            @Valid @RequestBody UpdateChapterRequest request) {
        return ResponseEntity.ok(courseService.updateChapter(currentUserId(), courseId, chapterId, request));
    }

    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    public ResponseEntity<Void> deleteChapter(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId) {
        courseService.deleteChapter(currentUserId(), courseId, chapterId);
        return ResponseEntity.noContent().build();
    }

    // ── Lesson ────────────────────────────────────────────────────────────────

    @PostMapping("/{courseId}/chapters/{chapterId}/lessons")
    public ResponseEntity<LessonResponse> addLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            @Valid @RequestBody CreateLessonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addLesson(currentUserId(), courseId, chapterId, request));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}")
    public ResponseEntity<LessonResponse> updateLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody UpdateLessonRequest request) {
        return ResponseEntity.ok(courseService.updateLesson(currentUserId(), courseId, chapterId, lessonId, request));
    }

    @DeleteMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}")
    public ResponseEntity<Void> deleteLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID chapterId,
            @PathVariable UUID lessonId) {
        courseService.deleteLesson(currentUserId(), courseId, chapterId, lessonId);
        return ResponseEntity.noContent().build();
    }

    // ── Lesson Resource ───────────────────────────────────────────────────────

    @PostMapping("/{courseId}/lessons/{lessonId}/resources/presign-upload")
    public ResponseEntity<ResourceUploadPresignResponse> requestUploadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ResourceUploadPresignRequest request) {
        return ResponseEntity.ok(lessonResourceService.requestUploadUrl(currentUserId(), courseId, lessonId, request));
    }

    @PostMapping("/{courseId}/lessons/{lessonId}/resources/confirm-upload")
    public ResponseEntity<LessonResourceResponse> confirmUpload(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody ResourceConfirmUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lessonResourceService.confirmUpload(currentUserId(), courseId, lessonId, request));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/resources")
    public ResponseEntity<List<LessonResourceResponse>> listResources(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId) {
        return ResponseEntity.ok(lessonResourceService.listResources(currentUserId(), courseId, lessonId));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/resources/{resourceId}/view-url")
    public ResponseEntity<ResourceDownloadUrlResponse> getViewUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId) {
        return ResponseEntity.ok(lessonResourceService.getViewUrl(currentUserId(), courseId, lessonId, resourceId));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/resources/{resourceId}/download-url")
    public ResponseEntity<ResourceDownloadUrlResponse> getDownloadUrl(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId) {
        return ResponseEntity.ok(lessonResourceService.getDownloadUrl(currentUserId(), courseId, lessonId, resourceId));
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}/resources/{resourceId}")
    public ResponseEntity<Void> deleteResource(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId) {
        lessonResourceService.deleteResource(currentUserId(), courseId, lessonId, resourceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{courseId}/lessons/{lessonId}/resources/{resourceId}")
    public ResponseEntity<LessonResourceResponse> renameResource(
            @PathVariable UUID courseId,
            @PathVariable UUID lessonId,
            @PathVariable UUID resourceId,
            @RequestBody java.util.Map<String, String> body) {
        String displayName = body.get("displayName");
        return ResponseEntity.ok(lessonResourceService.renameResource(currentUserId(), courseId, lessonId, resourceId, displayName));
    }

    // ── Categories ───────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<CourseCategory>> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAll()
                .stream().filter(c -> Boolean.TRUE.equals(c.getIsActive())).toList());
    }

    // ── Version history ───────────────────────────────────────────────────────

    @GetMapping("/{courseId}/history")
    public ResponseEntity<List<CourseApprovalLogResponse>> getCourseHistory(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.getCourseHistory(currentUserId(), courseId));
    }

    @GetMapping("/{courseId}/versions")
    public ResponseEntity<List<CourseVersionResponse>> getCourseVersions(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.getCourseVersions(currentUserId(), courseId));
    }

    @PostMapping("/{courseId}/versions/{versionId}/rollback")
    public ResponseEntity<CourseDetailResponse> rollbackToVersion(
            @PathVariable UUID courseId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(courseService.rollbackToVersion(currentUserId(), courseId, versionId));
    }

    @PostMapping("/{courseId}/versions/save-draft")
    public ResponseEntity<CourseVersionResponse> saveDraft(
            @PathVariable UUID courseId,
            @RequestParam(required = false) String label) {
        return ResponseEntity.ok(courseService.saveDraft(currentUserId(), courseId, label));
    }

    @GetMapping("/{courseId}/resources/presign-view")
    public ResponseEntity<java.util.Map<String, String>> presignViewResource(
            @PathVariable UUID courseId,
            @RequestParam String s3Key) {
        UUID userId = currentUserId();
        if (!courseRepository.existsByIdAndInstructorId(courseId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String url = s3Service.generatePresignedInlineUrl(s3Key, 3600).url().toString();
        return ResponseEntity.ok(java.util.Map.of("url", url));
    }

    @GetMapping("/{courseId}/resources/presign-download")
    public ResponseEntity<java.util.Map<String, String>> presignDownloadResource(
            @PathVariable UUID courseId,
            @RequestParam String s3Key) {
        UUID userId = currentUserId();
        if (!courseRepository.existsByIdAndInstructorId(courseId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String url = s3Service.generatePresignedGetUrl(s3Key, 3600).url().toString();
        return ResponseEntity.ok(java.util.Map.of("url", url));
    }

    @PatchMapping("/{courseId}/versions/{versionId}/label")
    public ResponseEntity<Void> renameDraftVersion(
            @PathVariable UUID courseId,
            @PathVariable UUID versionId,
            @RequestBody java.util.Map<String, String> body) {
        courseService.renameDraftVersion(currentUserId(), courseId, versionId, body.get("label"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{courseId}/versions/{versionId}/draft")
    public ResponseEntity<Void> deleteDraftVersion(
            @PathVariable UUID courseId,
            @PathVariable UUID versionId) {
        courseService.deleteDraftVersion(currentUserId(), courseId, versionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{courseId}/versions/{versionId}/submit")
    public ResponseEntity<CourseVersionResponse> submitVersion(
            @PathVariable UUID courseId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(courseService.submitVersion(currentUserId(), courseId, versionId));
    }

    @GetMapping("/{courseId}/versions/has-pending")
    public ResponseEntity<Boolean> hasPendingVersion(@PathVariable UUID courseId) {
        return ResponseEntity.ok(courseService.hasPendingVersion(currentUserId(), courseId));
    }

    @PostMapping("/{courseId}/versions/{versionId}/clone-as-draft")
    public ResponseEntity<CourseVersionResponse> cloneVersionAsDraft(
            @PathVariable UUID courseId,
            @PathVariable UUID versionId,
            @RequestParam(required = false) String label) {
        return ResponseEntity.ok(courseService.cloneVersionAsDraft(currentUserId(), courseId, versionId, label));
    }
}
