package project.lms_rikkei_edu.modules.course.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.request.ResourceConfirmUploadRequest;
import project.lms_rikkei_edu.modules.course.dto.request.ResourceUploadPresignRequest;
import project.lms_rikkei_edu.modules.course.dto.response.LessonResourceResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceDownloadUrlResponse;
import project.lms_rikkei_edu.modules.course.dto.response.ResourceUploadPresignResponse;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;
import project.lms_rikkei_edu.modules.course.exception.CourseNotOwnedException;
import project.lms_rikkei_edu.modules.course.exception.CourseStateException;
import project.lms_rikkei_edu.modules.course.exception.LessonNotFoundException;
import project.lms_rikkei_edu.modules.course.mapper.LessonResourceMapper;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonRepository;
import project.lms_rikkei_edu.modules.course.repository.LessonResourceRepository;
import project.lms_rikkei_edu.modules.course.service.LessonResourceService;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LessonResourceServiceImpl implements LessonResourceService {

    private static final long MAX_VIDEO_SIZE_BYTES  = 2L  * 1024 * 1024 * 1024; // 2GB
    private static final long MAX_DOC_SIZE_BYTES    = 200L * 1024 * 1024;        // 200MB

    private final LessonRepository lessonRepository;
    private final LessonResourceRepository lessonResourceRepository;
    private final CourseRepository courseRepository;
    private final S3Service s3Service;
    private final LessonResourceMapper lessonResourceMapper;
    private final CourseVersionReferenceChecker courseVersionReferenceChecker;

    @Value("${app.s3.presigned-url-expiry:3600}")
    private long presignedUrlExpiry;

    @Override
    public ResourceUploadPresignResponse requestUploadUrl(UUID instructorId, UUID courseId, UUID lessonId,
                                                          ResourceUploadPresignRequest request) {
        loadOwnedLesson(instructorId, courseId, lessonId);
        validateResourceTypeMatchesFile(request.getResourceType(), request.getOriginalFilename(), request.getMimeType());

        boolean isVideo = request.getResourceType() == ResourceType.VIDEO;
        long maxBytes = isVideo ? MAX_VIDEO_SIZE_BYTES : MAX_DOC_SIZE_BYTES;
        String maxLabel = isVideo ? "2GB" : "200MB";
        if (request.getFileSizeBytes() != null && request.getFileSizeBytes() > maxBytes) {
            throw new IllegalArgumentException("File vượt quá giới hạn " + maxLabel);
        }

        String extension = extractExtension(request.getOriginalFilename());
        String courseSlug = courseRepository.findById(courseId)
                .map(Course::getSlug).orElse(courseId.toString());
        String s3Key = buildS3Key(courseId, courseSlug, lessonId, extension);

        PresignedPutObjectRequest presigned = s3Service.generatePresignedPutUrl(
                s3Key, request.getMimeType(), presignedUrlExpiry);

        return ResourceUploadPresignResponse.builder()
                .presignedUrl(presigned.url().toString())
                .s3Key(s3Key)
                .contentType(request.getMimeType())
                .expiresAt(Instant.now().plusSeconds(presignedUrlExpiry))
                .build();
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public LessonResourceResponse confirmUpload(UUID instructorId, UUID courseId, UUID lessonId,
                                                ResourceConfirmUploadRequest request) {
        Lesson lesson = loadOwnedLesson(instructorId, courseId, lessonId);

        boolean isExternal = request.getExternalUrl() != null && !request.getExternalUrl().isBlank();
        String s3Key;
        if (isExternal) {
            s3Key = "ext://" + request.getExternalUrl().trim();
        } else {
            if (request.getS3Key() == null || request.getS3Key().isBlank()) {
                throw new IllegalArgumentException("Phải cung cấp s3Key hoặc externalUrl");
            }
            if (!s3Service.objectExists(request.getS3Key())) {
                throw new IllegalStateException("File chưa được upload lên S3: " + request.getS3Key());
            }
            // Chặn nguồn từ presign-upload cũng bị bỏ qua cross-check (VD gọi thẳng API, bỏ qua
            // FE) — resourceType client khai báo phải thực sự khớp với file đã upload, nếu không
            // học viên sẽ thấy 1 lesson video nhưng resource thật là PDF (hoặc ngược lại).
            validateResourceTypeMatchesFile(request.getResourceType(), request.getOriginalFilename(), request.getMimeType());
            s3Key = request.getS3Key();
        }

        int nextOrder = lessonResourceRepository
                .findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId).size() + 1;

        // Kiểm tra trạng thái khóa học trước khi tạo resource
        boolean isLiveCourse = courseRepository.findById(courseId)
                .map(c -> c.getStatus() == CourseStatus.PUBLISHED || c.getStatus() == CourseStatus.PENDING_UPDATE)
                .orElse(false);

        LessonResource resource = LessonResource.builder()
                .lesson(lesson)
                .courseId(courseId)
                .uploadedBy(instructorId)
                .s3Key(s3Key)
                .resourceType(request.getResourceType())
                .displayName(request.getDisplayName() != null
                        ? request.getDisplayName()
                        : request.getOriginalFilename())
                .originalFilename(request.getOriginalFilename())
                .fileSizeBytes(request.getFileSizeBytes())
                .mimeType(request.getMimeType())
                .isDownloadable(request.getIsDownloadable() != null ? request.getIsDownloadable() : true)
                .orderIndex(nextOrder)
                .status("ACTIVE")
                .uploadedAt(Instant.now())
                .isNewInUpdate(isLiveCourse)
                .build();

        LessonResource saved = lessonResourceRepository.save(resource);
        return lessonResourceMapper.toResponse(saved);
    }

    /** Chỉ check ownership, không block theo trạng thái course — dùng cho read-only operations. */
    private void verifyOwnership(UUID instructorId, UUID courseId) {
        courseRepository.findById(courseId).ifPresent(course -> {
            if (!course.getInstructorId().equals(instructorId)) {
                throw new CourseNotOwnedException();
            }
        });
    }

    public ResourceDownloadUrlResponse getDownloadUrl(UUID instructorId, UUID courseId, UUID lessonId,
                                                      UUID resourceId) {
        verifyOwnership(instructorId, courseId);

        LessonResource resource = lessonResourceRepository.findById(resourceId)
                .filter(r -> lessonId.equals(r.getLesson().getId()))
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Resource không tồn tại: " + resourceId));

        PresignedGetObjectRequest presigned = s3Service.generatePresignedGetUrl(resource.getS3Key(), presignedUrlExpiry);

        return ResourceDownloadUrlResponse.builder()
                .url(presigned.url().toString())
                .expiresAt(Instant.now().plusSeconds(presignedUrlExpiry))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceDownloadUrlResponse getViewUrl(UUID instructorId, UUID courseId, UUID lessonId, UUID resourceId) {
        verifyOwnership(instructorId, courseId);

        LessonResource resource = lessonResourceRepository.findById(resourceId)
                .filter(r -> lessonId.equals(r.getLesson().getId()))
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Resource không tồn tại: " + resourceId));

        PresignedGetObjectRequest presigned = s3Service.generatePresignedInlineUrl(resource.getS3Key(), presignedUrlExpiry);

        return ResourceDownloadUrlResponse.builder()
                .url(presigned.url().toString())
                .expiresAt(Instant.now().plusSeconds(presignedUrlExpiry))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonResourceResponse> listResources(UUID instructorId, UUID courseId, UUID lessonId) {
        loadOwnedLesson(instructorId, courseId, lessonId);
        return lessonResourceRepository
                .findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId)
                .stream()
                .filter(r -> !Boolean.TRUE.equals(r.getPendingDelete()))
                .map(lessonResourceMapper::toResponse)
                .toList();
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public void deleteResource(UUID instructorId, UUID courseId, UUID lessonId, UUID resourceId) {
        loadOwnedLesson(instructorId, courseId, lessonId);

        LessonResource resource = lessonResourceRepository.findById(resourceId)
                .filter(r -> lessonId.equals(r.getLesson().getId()))
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Resource không tồn tại: " + resourceId));

        // Kiểm tra khóa học đang live — nếu đang live thì chỉ đánh dấu pending_delete, không xóa thật
        boolean isLiveCourse = courseRepository.findById(resource.getCourseId())
                .map(c -> c.getStatus() == CourseStatus.PUBLISHED || c.getStatus() == CourseStatus.PENDING_UPDATE)
                .orElse(false);

        // Nếu resource này vừa được thêm trong lần update hiện tại → xóa thật luôn (không cần pending)
        boolean justAddedInUpdate = Boolean.TRUE.equals(resource.getIsNewInUpdate());

        if (isLiveCourse && !justAddedInUpdate) {
            // Đánh dấu chờ xóa — admin sẽ xóa thật khi duyệt
            resource.setPendingDelete(true);
            resource.setStatus("PENDING_DELETE");
            lessonResourceRepository.save(resource);
            return;
        }

        // Xóa ngay (course DRAFT hoặc resource vừa upload trong lần update này)
        resource.setDeletedAt(Instant.now());
        resource.setStatus("DELETED");
        lessonResourceRepository.save(resource);

        // Xóa thật trên S3 (bỏ qua external URL), chạy async (không chặn response) — DB là
        // nguồn sự thật, S3 chỉ dọn rác best-effort. Bỏ qua nếu còn CourseVersion nào (VD: 1 bản
        // nháp đã lưu trước đó) còn tham chiếu key này trong snapshot.
        String s3Key = resource.getS3Key();
        if (s3Key != null && !s3Key.startsWith("ext://")
                && courseVersionReferenceChecker.isSafeToDelete(courseId, s3Key)) {
            s3Service.deleteObjectAsync(s3Key);
        }
    }

    @Override
    @CacheEvict(value = "course-detail", key = "#courseId + '_' + #instructorId")
    public LessonResourceResponse renameResource(UUID instructorId, UUID courseId, UUID lessonId, UUID resourceId, String displayName) {
        loadOwnedLesson(instructorId, courseId, lessonId);

        LessonResource resource = lessonResourceRepository.findById(resourceId)
                .filter(r -> lessonId.equals(r.getLesson().getId()))
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Resource không tồn tại: " + resourceId));

        if (displayName != null && !displayName.isBlank()) {
            resource.setDisplayName(displayName.trim());
            lessonResourceRepository.save(resource);
        }
        return lessonResourceMapper.toResponse(resource);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Lesson loadOwnedLesson(UUID instructorId, UUID courseId, UUID lessonId) {
        Lesson lesson = lessonRepository.findByIdAndCourseId(lessonId, courseId)
                .orElseThrow(() -> new LessonNotFoundException(lessonId));

        courseRepository.findById(courseId).ifPresent(course -> {
            if (!course.getInstructorId().equals(instructorId)) {
                throw new CourseNotOwnedException();
            }
            if (course.getStatus() == CourseStatus.PENDING_UPDATE) {
                throw new CourseStateException("Không thể chỉnh sửa tài liệu khi khóa học đang chờ duyệt cập nhật");
            }
            if (course.getStatus() == CourseStatus.PENDING) {
                throw new CourseStateException("Không thể chỉnh sửa tài liệu khi khóa học đang chờ duyệt lần đầu");
            }
        });

        return lesson;
    }

    private String buildS3Key(UUID courseId, String courseSlug, UUID lessonId, String extension) {
        return String.format("courses/%s-%s/lessons/%s/resources/%s%s",
                courseId, courseSlug, lessonId, UUID.randomUUID(), extension);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Frontend đã tự kiểm tra loại file khớp với tab Video/Tài liệu đang thao tác, nhưng đó chỉ là
     * UX — client luôn có thể bị bypass (gọi thẳng API). Đây là biên giới tin cậy thật: chặn
     * trường hợp resourceType client khai báo (VIDEO/PDF/DOC/...) không khớp với phần mở rộng/
     * mimeType file thực tế đã upload, để tránh 1 lesson bị gắn nhầm loại resource (VD kéo thả
     * PDF vào khung Video, hoặc video vào khung Tài liệu).
     */
    private void validateResourceTypeMatchesFile(ResourceType resourceType, String originalFilename, String mimeType) {
        if (resourceType == null) return;

        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase()
                : "";
        String mime = mimeType != null ? mimeType.toLowerCase() : "";

        // OTHER là fallback cho định dạng không nhận diện được — không có tập extension/mimeType
        // "đúng" cố định để so khớp nên luôn coi là hợp lệ, không chặn cứng.
        boolean matches = switch (resourceType) {
            case VIDEO -> mime.startsWith("video/") || List.of("mp4", "mov", "webm").contains(ext);
            case PDF -> mime.equals("application/pdf") || ext.equals("pdf");
            case DOC -> ext.equals("doc") || ext.equals("docx");
            case SLIDE -> ext.equals("ppt") || ext.equals("pptx");
            case IMAGE -> mime.startsWith("image/") || List.of("png", "jpg", "jpeg", "gif", "webp").contains(ext);
            case OTHER -> true;
        };

        if (!matches) {
            throw new BusinessException("Loại file không khớp với loại tài nguyên đã chọn (" + resourceType + ")");
        }
    }
}
