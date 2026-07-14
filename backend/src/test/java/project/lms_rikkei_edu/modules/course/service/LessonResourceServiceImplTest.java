package project.lms_rikkei_edu.modules.course.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
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
import project.lms_rikkei_edu.modules.course.service.impl.CourseVersionReferenceChecker;
import project.lms_rikkei_edu.modules.course.service.impl.LessonResourceServiceImpl;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonResourceServiceImplTest {

    @Mock LessonRepository lessonRepository;
    @Mock LessonResourceRepository lessonResourceRepository;
    @Mock CourseRepository courseRepository;
    @Mock S3Service s3Service;
    @Mock LessonResourceMapper lessonResourceMapper;
    @Mock CourseVersionReferenceChecker courseVersionReferenceChecker;

    LessonResourceServiceImpl service;

    private final UUID instructorId = UUID.randomUUID();
    private final UUID courseId     = UUID.randomUUID();
    private final UUID lessonId     = UUID.randomUUID();
    private final UUID resourceId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LessonResourceServiceImpl(
                lessonRepository, lessonResourceRepository, courseRepository,
                s3Service, lessonResourceMapper, courseVersionReferenceChecker
        );
        ReflectionTestUtils.setField(service, "presignedUrlExpiry", 3600L);
        when(courseVersionReferenceChecker.isSafeToDelete(any(), any())).thenReturn(true);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Lesson lesson() {
        Lesson l = new Lesson();
        l.setId(lessonId);
        l.setCourseId(courseId);
        return l;
    }

    private Course draftCourse() {
        Course c = new Course();
        c.setId(courseId);
        c.setInstructorId(instructorId);
        c.setSlug("test-course");
        c.setStatus(CourseStatus.DRAFT);
        return c;
    }

    private Course publishedCourse() {
        Course c = draftCourse();
        c.setStatus(CourseStatus.PUBLISHED);
        return c;
    }

    private LessonResource activeResource(String s3Key) {
        LessonResource r = new LessonResource();
        r.setId(resourceId);
        r.setLesson(lesson());
        r.setCourseId(courseId);
        r.setS3Key(s3Key);
        r.setStatus("ACTIVE");
        return r;
    }

    private LessonResourceResponse resourceResponse() {
        return LessonResourceResponse.builder()
                .id(resourceId).displayName("file.pdf")
                .resourceType(ResourceType.PDF).build();
    }

    // stub loadOwnedLesson success (DRAFT course owned by instructor)
    private void stubOwnedLesson() {
        when(lessonRepository.findByIdAndCourseId(lessonId, courseId))
                .thenReturn(Optional.of(lesson()));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
    }

    // ── requestUploadUrl ──────────────────────────────────────────────────────

    @Nested
    class RequestUploadUrl {

        @Test
        void returnsPresignedPutUrl() throws Exception {
            stubOwnedLesson();
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
            when(presigned.url()).thenReturn(URI.create("https://s3.example.com/put").toURL());
            when(s3Service.generatePresignedPutUrl(anyString(), anyString(), anyLong()))
                    .thenReturn(presigned);

            ResourceUploadPresignRequest req = new ResourceUploadPresignRequest();
            req.setOriginalFilename("lecture.pdf");
            req.setMimeType("application/pdf");
            req.setFileSizeBytes(1024L);
            req.setResourceType(ResourceType.PDF);

            ResourceUploadPresignResponse resp = service.requestUploadUrl(instructorId, courseId, lessonId, req);

            assertThat(resp.getPresignedUrl()).contains("s3.example.com");
            assertThat(resp.getS3Key()).contains("courses/");
            assertThat(resp.getContentType()).isEqualTo("application/pdf");
        }

        @Test
        void throws_whenFileSizeExceedsDocLimit() {
            stubOwnedLesson();

            ResourceUploadPresignRequest req = new ResourceUploadPresignRequest();
            req.setOriginalFilename("big.pdf");
            req.setMimeType("application/pdf");
            req.setFileSizeBytes(300L * 1024 * 1024); // 300MB > 200MB limit
            req.setResourceType(ResourceType.PDF);

            assertThatThrownBy(() -> service.requestUploadUrl(instructorId, courseId, lessonId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("200MB");
        }

        @Test
        void throws_whenFileSizeExceedsVideoLimit() {
            stubOwnedLesson();

            ResourceUploadPresignRequest req = new ResourceUploadPresignRequest();
            req.setOriginalFilename("big.mp4");
            req.setMimeType("video/mp4");
            req.setFileSizeBytes(3L * 1024 * 1024 * 1024); // 3GB > 2GB limit
            req.setResourceType(ResourceType.VIDEO);

            assertThatThrownBy(() -> service.requestUploadUrl(instructorId, courseId, lessonId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2GB");
        }

        @Test
        void throws_whenLessonNotFound() {
            when(lessonRepository.findByIdAndCourseId(lessonId, courseId))
                    .thenReturn(Optional.empty());

            ResourceUploadPresignRequest req = new ResourceUploadPresignRequest();
            req.setOriginalFilename("file.pdf");
            req.setMimeType("application/pdf");
            req.setFileSizeBytes(1024L);
            req.setResourceType(ResourceType.PDF);

            assertThatThrownBy(() -> service.requestUploadUrl(instructorId, courseId, lessonId, req))
                    .isInstanceOf(LessonNotFoundException.class);
        }

        @Test
        void throws_whenNotOwner() {
            UUID otherId = UUID.randomUUID();
            Lesson l = lesson();
            Course c = draftCourse();
            c.setInstructorId(otherId);

            when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(l));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(c));

            ResourceUploadPresignRequest req = new ResourceUploadPresignRequest();
            req.setOriginalFilename("file.pdf");
            req.setMimeType("application/pdf");
            req.setFileSizeBytes(1024L);
            req.setResourceType(ResourceType.PDF);

            assertThatThrownBy(() -> service.requestUploadUrl(instructorId, courseId, lessonId, req))
                    .isInstanceOf(CourseNotOwnedException.class);
        }

        @Test
        void throws_whenCourseIsPending() {
            Course c = draftCourse();
            c.setStatus(CourseStatus.PENDING);

            when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson()));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(c));

            ResourceUploadPresignRequest req = new ResourceUploadPresignRequest();
            req.setOriginalFilename("file.pdf");
            req.setMimeType("application/pdf");
            req.setFileSizeBytes(1024L);
            req.setResourceType(ResourceType.PDF);

            assertThatThrownBy(() -> service.requestUploadUrl(instructorId, courseId, lessonId, req))
                    .isInstanceOf(CourseStateException.class);
        }

        @Test
        void throws_whenCourseIsPendingUpdate() {
            Course c = draftCourse();
            c.setStatus(CourseStatus.PENDING_UPDATE);

            when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson()));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(c));

            ResourceUploadPresignRequest req = new ResourceUploadPresignRequest();
            req.setOriginalFilename("file.pdf");
            req.setMimeType("application/pdf");
            req.setFileSizeBytes(1024L);
            req.setResourceType(ResourceType.PDF);

            assertThatThrownBy(() -> service.requestUploadUrl(instructorId, courseId, lessonId, req))
                    .isInstanceOf(CourseStateException.class);
        }
    }

    // ── confirmUpload ─────────────────────────────────────────────────────────

    @Nested
    class ConfirmUpload {

        @Test
        void savesResourceWithS3Key_whenFileUploaded() {
            stubOwnedLesson();
            when(s3Service.objectExists("courses/file.pdf")).thenReturn(true);
            when(lessonResourceRepository.findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId))
                    .thenReturn(List.of());
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            LessonResource saved = activeResource("courses/file.pdf");
            when(lessonResourceRepository.save(any())).thenReturn(saved);
            when(lessonResourceMapper.toResponse(saved)).thenReturn(resourceResponse());

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("courses/file.pdf");
            req.setResourceType(ResourceType.PDF);
            req.setOriginalFilename("lecture.pdf");
            req.setMimeType("application/pdf");
            req.setDisplayName("Lecture Notes");

            LessonResourceResponse resp = service.confirmUpload(instructorId, courseId, lessonId, req);

            assertThat(resp.getId()).isEqualTo(resourceId);
            verify(lessonResourceRepository).save(argThat(r ->
                    "courses/file.pdf".equals(r.getS3Key()) && "ACTIVE".equals(r.getStatus())
            ));
        }

        @Test
        void savesResourceWithExternalUrl_whenExternalUrlProvided() {
            stubOwnedLesson();
            when(lessonResourceRepository.findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId))
                    .thenReturn(List.of());
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            LessonResource saved = activeResource("ext://https://youtube.com/watch?v=abc");
            when(lessonResourceRepository.save(any())).thenReturn(saved);
            when(lessonResourceMapper.toResponse(saved)).thenReturn(resourceResponse());

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setExternalUrl("https://youtube.com/watch?v=abc");
            req.setResourceType(ResourceType.VIDEO);

            service.confirmUpload(instructorId, courseId, lessonId, req);

            verify(lessonResourceRepository).save(argThat(r ->
                    r.getS3Key().startsWith("ext://")
            ));
            verify(s3Service, never()).objectExists(any());
        }

        @Test
        void setsIsNewInUpdate_whenCourseIsPublished() {
            when(lessonRepository.findByIdAndCourseId(lessonId, courseId)).thenReturn(Optional.of(lesson()));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));
            when(s3Service.objectExists("courses/file.pdf")).thenReturn(true);
            when(lessonResourceRepository.findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId))
                    .thenReturn(List.of());

            LessonResource saved = activeResource("courses/file.pdf");
            saved.setIsNewInUpdate(true);
            when(lessonResourceRepository.save(any())).thenReturn(saved);
            when(lessonResourceMapper.toResponse(saved)).thenReturn(resourceResponse());

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("courses/file.pdf");
            req.setResourceType(ResourceType.PDF);
            req.setOriginalFilename("lecture.pdf");
            req.setMimeType("application/pdf");

            service.confirmUpload(instructorId, courseId, lessonId, req);

            verify(lessonResourceRepository).save(argThat(r -> Boolean.TRUE.equals(r.getIsNewInUpdate())));
        }

        @Test
        void setsOrderIndexCorrectly() {
            stubOwnedLesson();
            // 2 resources already exist → next order = 3
            when(lessonResourceRepository.findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId))
                    .thenReturn(List.of(activeResource("k1"), activeResource("k2")));
            when(s3Service.objectExists("courses/file.pdf")).thenReturn(true);
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            LessonResource saved = activeResource("courses/file.pdf");
            when(lessonResourceRepository.save(any())).thenReturn(saved);
            when(lessonResourceMapper.toResponse(saved)).thenReturn(resourceResponse());

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("courses/file.pdf");
            req.setResourceType(ResourceType.PDF);
            req.setOriginalFilename("lecture.pdf");
            req.setMimeType("application/pdf");

            service.confirmUpload(instructorId, courseId, lessonId, req);

            verify(lessonResourceRepository).save(argThat(r -> r.getOrderIndex() == 3));
        }

        @Test
        void throws_whenS3KeyBlankAndNoExternalUrl() {
            stubOwnedLesson();
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("");
            req.setResourceType(ResourceType.PDF);

            assertThatThrownBy(() -> service.confirmUpload(instructorId, courseId, lessonId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("s3Key");
        }

        @Test
        void throws_whenFileNotUploadedToS3() {
            stubOwnedLesson();
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            when(s3Service.objectExists("courses/missing.pdf")).thenReturn(false);

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("courses/missing.pdf");
            req.setResourceType(ResourceType.PDF);

            assertThatThrownBy(() -> service.confirmUpload(instructorId, courseId, lessonId, req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("S3");
        }

        @Test
        void throws_whenResourceTypeVideoButFileIsPdf() {
            // Regression: chặn nhầm PDF được khai báo resourceType=VIDEO (VD kéo thả PDF vào
            // khung Video ở FE rồi bypass check client, hoặc gọi thẳng API).
            stubOwnedLesson();
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            when(s3Service.objectExists("courses/file.pdf")).thenReturn(true);

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("courses/file.pdf");
            req.setResourceType(ResourceType.VIDEO);
            req.setOriginalFilename("slides.pdf");
            req.setMimeType("application/pdf");

            assertThatThrownBy(() -> service.confirmUpload(instructorId, courseId, lessonId, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("không khớp");
        }

        @Test
        void throws_whenResourceTypePdfButFileIsVideo() {
            // Ngược lại — video bị khai báo nhầm resourceType=PDF (VD kéo thả video vào khung
            // Tài liệu).
            stubOwnedLesson();
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            when(s3Service.objectExists("courses/file.mp4")).thenReturn(true);

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("courses/file.mp4");
            req.setResourceType(ResourceType.PDF);
            req.setOriginalFilename("lecture.mp4");
            req.setMimeType("video/mp4");

            assertThatThrownBy(() -> service.confirmUpload(instructorId, courseId, lessonId, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("không khớp");
        }

        @Test
        void allowsMatchByMimeType_whenFilenameHasNoExtension() {
            // originalFilename không có dấu "." (hoặc null) — vẫn phải khớp được nhờ mimeType,
            // không phụ thuộc hoàn toàn vào extension.
            stubOwnedLesson();
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            when(s3Service.objectExists("courses/file")).thenReturn(true);
            when(lessonResourceRepository.findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId))
                    .thenReturn(List.of());
            LessonResource saved = activeResource("courses/file");
            when(lessonResourceRepository.save(any())).thenReturn(saved);
            when(lessonResourceMapper.toResponse(saved)).thenReturn(resourceResponse());

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("courses/file");
            req.setResourceType(ResourceType.PDF);
            req.setOriginalFilename("document-without-extension");
            req.setMimeType("application/pdf");

            assertThatNoException().isThrownBy(() -> service.confirmUpload(instructorId, courseId, lessonId, req));
        }

        @Test
        void allowsOtherResourceType_withoutStrictValidation() {
            // OTHER là fallback cho các định dạng không nhận diện được — không chặn cứng vì
            // không có tập extension/mimeType "đúng" để so khớp.
            stubOwnedLesson();
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            when(s3Service.objectExists("courses/file.xyz")).thenReturn(true);
            when(lessonResourceRepository.findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId))
                    .thenReturn(List.of());
            LessonResource saved = activeResource("courses/file.xyz");
            when(lessonResourceRepository.save(any())).thenReturn(saved);
            when(lessonResourceMapper.toResponse(saved)).thenReturn(resourceResponse());

            ResourceConfirmUploadRequest req = new ResourceConfirmUploadRequest();
            req.setS3Key("courses/file.xyz");
            req.setResourceType(ResourceType.OTHER);
            req.setOriginalFilename("data.xyz");
            req.setMimeType("application/octet-stream");

            assertThatNoException().isThrownBy(() -> service.confirmUpload(instructorId, courseId, lessonId, req));
        }
    }

    // ── getDownloadUrl ────────────────────────────────────────────────────────

    @Nested
    class GetDownloadUrl {

        @Test
        void returnsPresignedGetUrl() throws Exception {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            LessonResource r = activeResource("courses/file.pdf");

            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(URI.create("https://s3.example.com/get").toURL());
            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(s3Service.generatePresignedGetUrl("courses/file.pdf", 3600L)).thenReturn(presigned);

            ResourceDownloadUrlResponse resp = service.getDownloadUrl(instructorId, courseId, lessonId, resourceId);

            assertThat(resp.getUrl()).contains("s3.example.com");
            assertThat(resp.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        void throws_whenResourceNotFound() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDownloadUrl(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throws_whenResourceBelongsToDifferentLesson() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            Lesson otherLesson = new Lesson();
            otherLesson.setId(UUID.randomUUID()); // different lesson
            LessonResource r = activeResource("courses/file.pdf");
            r.setLesson(otherLesson);

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.getDownloadUrl(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throws_whenResourceDeleted() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            LessonResource r = activeResource("courses/file.pdf");
            r.setDeletedAt(Instant.now());
            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.getDownloadUrl(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throws_whenNotOwner() {
            Course c = draftCourse();
            c.setInstructorId(UUID.randomUUID());
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.getDownloadUrl(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(CourseNotOwnedException.class);
        }
    }

    // ── getViewUrl ────────────────────────────────────────────────────────────

    @Nested
    class GetViewUrl {

        @Test
        void returnsPresignedInlineUrl() throws Exception {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            LessonResource r = activeResource("courses/file.pdf");

            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(URI.create("https://s3.example.com/inline").toURL());
            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(s3Service.generatePresignedInlineUrl("courses/file.pdf", 3600L)).thenReturn(presigned);

            ResourceDownloadUrlResponse resp = service.getViewUrl(instructorId, courseId, lessonId, resourceId);

            assertThat(resp.getUrl()).contains("s3.example.com");
        }

        @Test
        void throws_whenResourceNotFound() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getViewUrl(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throws_whenResourceBelongsToDifferentLesson() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            Lesson otherLesson = new Lesson();
            otherLesson.setId(UUID.randomUUID()); // different lesson
            LessonResource r = activeResource("courses/file.pdf");
            r.setLesson(otherLesson);

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.getViewUrl(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throws_whenResourceDeleted() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            LessonResource r = activeResource("courses/file.pdf");
            r.setDeletedAt(Instant.now());
            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.getViewUrl(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throws_whenNotOwner() {
            Course c = draftCourse();
            c.setInstructorId(UUID.randomUUID());
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.getViewUrl(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(CourseNotOwnedException.class);
        }
    }

    // ── listResources ─────────────────────────────────────────────────────────

    @Nested
    class ListResources {

        @Test
        void returnsActiveResources_excludingPendingDelete() {
            stubOwnedLesson();

            LessonResource active  = activeResource("courses/a.pdf");
            LessonResource pending = activeResource("courses/b.pdf");
            pending.setPendingDelete(true);

            when(lessonResourceRepository.findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId))
                    .thenReturn(List.of(active, pending));
            when(lessonResourceMapper.toResponse(active)).thenReturn(resourceResponse());

            List<LessonResourceResponse> result = service.listResources(instructorId, courseId, lessonId);

            assertThat(result).hasSize(1);
        }

        @Test
        void returnsEmptyList_whenNoResources() {
            stubOwnedLesson();
            when(lessonResourceRepository.findAllByLessonIdAndDeletedAtIsNullOrderByOrderIndexAsc(lessonId))
                    .thenReturn(List.of());

            List<LessonResourceResponse> result = service.listResources(instructorId, courseId, lessonId);

            assertThat(result).isEmpty();
        }
    }

    // ── deleteResource ────────────────────────────────────────────────────────

    @Nested
    class DeleteResource {

        @Test
        void hardDeletes_whenCourseIsDraft() {
            stubOwnedLesson();
            LessonResource r = activeResource("courses/file.pdf");

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            service.deleteResource(instructorId, courseId, lessonId, resourceId);

            assertThat(r.getDeletedAt()).isNotNull();
            assertThat(r.getStatus()).isEqualTo("DELETED");
            verify(s3Service).deleteObjectAsync("courses/file.pdf");
        }

        @Test
        void marksPendingDelete_whenCourseIsPublished() {
            stubOwnedLesson();
            LessonResource r = activeResource("courses/file.pdf");
            r.setIsNewInUpdate(false);

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));

            service.deleteResource(instructorId, courseId, lessonId, resourceId);

            assertThat(r.getPendingDelete()).isTrue();
            assertThat(r.getStatus()).isEqualTo("PENDING_DELETE");
            verify(s3Service, never()).deleteObjectAsync(any());
        }

        @Test
        void hardDeletes_whenResourceIsNewInUpdate_evenIfCoursePublished() {
            stubOwnedLesson();
            LessonResource r = activeResource("courses/new.pdf");
            r.setIsNewInUpdate(true);

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(publishedCourse()));

            service.deleteResource(instructorId, courseId, lessonId, resourceId);

            assertThat(r.getDeletedAt()).isNotNull();
            verify(s3Service).deleteObjectAsync("courses/new.pdf");
        }

        @Test
        void skipsS3Delete_whenExternalUrl() {
            stubOwnedLesson();
            LessonResource r = activeResource("ext://https://youtube.com/watch?v=abc");

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));

            service.deleteResource(instructorId, courseId, lessonId, resourceId);

            assertThat(r.getDeletedAt()).isNotNull();
            verify(s3Service, never()).deleteObjectAsync(any());
        }

        @Test
        void throws_whenResourceNotFound() {
            stubOwnedLesson();
            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteResource(instructorId, courseId, lessonId, resourceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        // Vẫn soft-delete DB row (không còn hiện cho ai) nhưng KHÔNG xóa file S3 nếu còn 1
        // CourseVersion nào đó (VD: một bản nháp đã lưu trước đó) còn tham chiếu key này.
        @Test
        void keepsS3File_whenStillReferencedByAnotherCourseVersion() {
            stubOwnedLesson();
            LessonResource r = activeResource("courses/file.pdf");

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(draftCourse()));
            when(courseVersionReferenceChecker.isSafeToDelete(courseId, "courses/file.pdf")).thenReturn(false);

            service.deleteResource(instructorId, courseId, lessonId, resourceId);

            assertThat(r.getDeletedAt()).isNotNull();
            assertThat(r.getStatus()).isEqualTo("DELETED");
            verify(s3Service, never()).deleteObjectAsync(any());
        }
    }

    // ── renameResource ────────────────────────────────────────────────────────

    @Nested
    class RenameResource {

        @Test
        void updatesDisplayName() {
            stubOwnedLesson();
            LessonResource r = activeResource("courses/file.pdf");
            r.setDisplayName("Old Name");

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(lessonResourceRepository.save(r)).thenReturn(r);
            when(lessonResourceMapper.toResponse(r)).thenReturn(resourceResponse());

            service.renameResource(instructorId, courseId, lessonId, resourceId, "New Name");

            assertThat(r.getDisplayName()).isEqualTo("New Name");
            verify(lessonResourceRepository).save(r);
        }

        @Test
        void skipsUpdate_whenNewNameBlank() {
            stubOwnedLesson();
            LessonResource r = activeResource("courses/file.pdf");
            r.setDisplayName("Old Name");

            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.of(r));
            when(lessonResourceMapper.toResponse(r)).thenReturn(resourceResponse());

            service.renameResource(instructorId, courseId, lessonId, resourceId, "  ");

            assertThat(r.getDisplayName()).isEqualTo("Old Name");
            verify(lessonResourceRepository, never()).save(r);
        }

        @Test
        void throws_whenResourceNotFound() {
            stubOwnedLesson();
            when(lessonResourceRepository.findById(resourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.renameResource(instructorId, courseId, lessonId, resourceId, "Name"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
