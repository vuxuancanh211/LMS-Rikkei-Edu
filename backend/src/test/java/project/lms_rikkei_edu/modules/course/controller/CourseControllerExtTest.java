package project.lms_rikkei_edu.modules.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.course.dto.request.*;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.enums.LessonType;
import project.lms_rikkei_edu.modules.course.exception.ChapterNotFoundException;
import project.lms_rikkei_edu.modules.course.exception.CourseNotFoundException;
import project.lms_rikkei_edu.modules.course.exception.LessonNotFoundException;
import project.lms_rikkei_edu.modules.course.repository.CourseCategoryRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.service.CourseService;
import project.lms_rikkei_edu.modules.course.service.LessonResourceService;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseControllerExtTest {

    private CourseService courseService;
    private LessonResourceService lessonResourceService;
    private S3Service s3Service;
    private CourseCategoryRepository catRepo;
    private CourseRepository courseRepo;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID instructorId = UUID.randomUUID();
    private final UUID courseId     = UUID.randomUUID();
    private final UUID chapterId    = UUID.randomUUID();
    private final UUID lessonId     = UUID.randomUUID();
    private final UUID versionId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        courseService         = mock(CourseService.class);
        lessonResourceService = mock(LessonResourceService.class);
        s3Service             = mock(S3Service.class);
        catRepo               = mock(CourseCategoryRepository.class);
        courseRepo            = mock(CourseRepository.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(instructorId));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CourseController(
                        courseService, lessonResourceService, currentUserProvider,
                        s3Service, catRepo, courseRepo))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private CourseDetailResponse detailResponse() {
        return CourseDetailResponse.builder()
                .id(courseId).title("Course").status(CourseStatus.PUBLISHED)
                .chapters(List.of()).build();
    }

    private ChapterResponse chapterResponse() {
        return ChapterResponse.builder().id(chapterId).title("Chapter").orderIndex(1).build();
    }

    private LessonResponse lessonResponse() {
        return LessonResponse.builder().id(lessonId).title("Lesson").build();
    }

    private CourseVersionResponse versionResponse() {
        return CourseVersionResponse.builder()
                .id(versionId).status("DRAFT").submittedAt(Instant.now()).build();
    }

    // ── PUT /{courseId}/chapters/{chapterId} ──────────────────────────────────

    @Nested
    class UpdateChapter {
        @Test
        void returns200_whenUpdated() throws Exception {
            when(courseService.updateChapter(eq(instructorId), eq(courseId), eq(chapterId), any()))
                    .thenReturn(chapterResponse());

            mockMvc.perform(put("/api/instructor/courses/{c}/chapters/{ch}", courseId, chapterId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Updated Chapter\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(chapterId.toString()));
        }

        @Test
        void returns404_whenChapterNotFound() throws Exception {
            when(courseService.updateChapter(any(), any(), any(), any()))
                    .thenThrow(new ChapterNotFoundException(chapterId));

            mockMvc.perform(put("/api/instructor/courses/{c}/chapters/{ch}", courseId, chapterId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Valid Chapter Title\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── POST /{courseId}/chapters/{chapterId}/lessons ─────────────────────────

    @Nested
    class AddLesson {
        @Test
        void returns201_whenAdded() throws Exception {
            when(courseService.addLesson(eq(instructorId), eq(courseId), eq(chapterId), any()))
                    .thenReturn(lessonResponse());

            CreateLessonRequest req = new CreateLessonRequest();
            req.setTitle("New Lesson"); req.setType(LessonType.TEXT);

            mockMvc.perform(post("/api/instructor/courses/{c}/chapters/{ch}/lessons", courseId, chapterId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(lessonId.toString()));
        }

        @Test
        void returns404_whenChapterNotFound() throws Exception {
            when(courseService.addLesson(any(), any(), any(), any()))
                    .thenThrow(new ChapterNotFoundException(chapterId));

            CreateLessonRequest req = new CreateLessonRequest();
            req.setTitle("Valid Lesson Title"); req.setType(LessonType.TEXT);

            mockMvc.perform(post("/api/instructor/courses/{c}/chapters/{ch}/lessons", courseId, chapterId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── PUT /{courseId}/chapters/{chapterId}/lessons/{lessonId} ───────────────

    @Nested
    class UpdateLesson {
        @Test
        void returns200_whenUpdated() throws Exception {
            when(courseService.updateLesson(eq(instructorId), eq(courseId), eq(chapterId), eq(lessonId), any()))
                    .thenReturn(lessonResponse());

            mockMvc.perform(put("/api/instructor/courses/{c}/chapters/{ch}/lessons/{l}",
                            courseId, chapterId, lessonId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Updated\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void returns404_whenLessonNotFound() throws Exception {
            when(courseService.updateLesson(any(), any(), any(), any(), any()))
                    .thenThrow(new LessonNotFoundException(lessonId));

            mockMvc.perform(put("/api/instructor/courses/{c}/chapters/{ch}/lessons/{l}",
                            courseId, chapterId, lessonId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Valid Title Here\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /{courseId}/chapters/{chapterId}/lessons/{lessonId} ────────────

    @Nested
    class DeleteLesson {
        @Test
        void returns204_whenDeleted() throws Exception {
            doNothing().when(courseService).deleteLesson(instructorId, courseId, chapterId, lessonId);

            mockMvc.perform(delete("/api/instructor/courses/{c}/chapters/{ch}/lessons/{l}",
                            courseId, chapterId, lessonId))
                    .andExpect(status().isNoContent());
        }
    }

    // ── GET /{courseId}/history ───────────────────────────────────────────────

    @Nested
    class GetHistory {
        @Test
        void returns200_withHistory() throws Exception {
            CourseApprovalLogResponse log = CourseApprovalLogResponse.builder()
                    .id(UUID.randomUUID()).action("APPROVED").actorType("ADMIN").build();

            when(courseService.getCourseHistory(instructorId, courseId)).thenReturn(List.of(log));

            mockMvc.perform(get("/api/instructor/courses/{c}/history", courseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].action").value("APPROVED"));
        }

        @Test
        void returns200_emptyList() throws Exception {
            when(courseService.getCourseHistory(instructorId, courseId)).thenReturn(List.of());

            mockMvc.perform(get("/api/instructor/courses/{c}/history", courseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ── GET /{courseId}/versions ──────────────────────────────────────────────

    @Nested
    class GetVersions {
        @Test
        void returns200_withVersions() throws Exception {
            when(courseService.getCourseVersions(instructorId, courseId))
                    .thenReturn(List.of(versionResponse()));

            mockMvc.perform(get("/api/instructor/courses/{c}/versions", courseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("DRAFT"));
        }
    }

    // ── POST /{courseId}/versions/{versionId}/rollback ────────────────────────

    @Nested
    class Rollback {
        @Test
        void returns200_whenRolledBack() throws Exception {
            when(courseService.rollbackToVersion(instructorId, courseId, versionId))
                    .thenReturn(detailResponse());

            mockMvc.perform(post("/api/instructor/courses/{c}/versions/{v}/rollback",
                            courseId, versionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(courseId.toString()));
        }
    }

    // ── POST /{courseId}/versions/save-draft ──────────────────────────────────

    @Nested
    class SaveDraft {
        @Test
        void returns200_whenDraftSaved() throws Exception {
            when(courseService.saveDraft(instructorId, courseId, "My Draft"))
                    .thenReturn(versionResponse());

            mockMvc.perform(post("/api/instructor/courses/{c}/versions/save-draft", courseId)
                            .param("label", "My Draft"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }
    }

    // ── PATCH /{courseId}/versions/{versionId}/label ──────────────────────────

    @Nested
    class RenameDraftVersion {
        @Test
        void returns204_whenRenamed() throws Exception {
            doNothing().when(courseService).renameDraftVersion(instructorId, courseId, versionId, "New");

            mockMvc.perform(patch("/api/instructor/courses/{c}/versions/{v}/label",
                            courseId, versionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"New\"}"))
                    .andExpect(status().isNoContent());
        }
    }

    // ── DELETE /{courseId}/versions/{versionId}/draft ─────────────────────────

    @Nested
    class DeleteDraftVersion {
        @Test
        void returns204_whenDeleted() throws Exception {
            doNothing().when(courseService).deleteDraftVersion(instructorId, courseId, versionId);

            mockMvc.perform(delete("/api/instructor/courses/{c}/versions/{v}/draft",
                            courseId, versionId))
                    .andExpect(status().isNoContent());
        }
    }

    // ── POST /{courseId}/versions/{versionId}/submit ──────────────────────────

    @Nested
    class SubmitVersion {
        @Test
        void returns200_whenSubmitted() throws Exception {
            CourseVersionResponse pending = CourseVersionResponse.builder()
                    .id(versionId).status("PENDING").build();
            when(courseService.submitVersion(instructorId, courseId, versionId)).thenReturn(pending);

            mockMvc.perform(post("/api/instructor/courses/{c}/versions/{v}/submit",
                            courseId, versionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }
    }

    // ── POST /presign-thumbnail ───────────────────────────────────────────────

    @Nested
    class PresignThumbnail {
        @Test
        void returns200_withUploadUrl() throws Exception {
            PresignedPutObjectRequest putReq = mock(PresignedPutObjectRequest.class);
            when(putReq.url()).thenReturn(new URL("https://s3.example.com/upload"));
            PresignedGetObjectRequest getReq = mock(PresignedGetObjectRequest.class);
            when(getReq.url()).thenReturn(new URL("https://s3.example.com/view"));
            when(s3Service.generatePresignedPutUrl(anyString(), anyString(), anyLong()))
                    .thenReturn(putReq);
            when(s3Service.generatePresignedGetUrl(anyString(), anyLong()))
                    .thenReturn(getReq);

            mockMvc.perform(post("/api/instructor/courses/presign-thumbnail")
                            .param("mimeType", "image/jpeg"))
                    .andExpect(status().isOk());
        }
    }

    // ── GET /{courseId}/resources/presign-view ────────────────────────────────

    @Nested
    class PresignView {
        @Test
        void returns200_whenOwner() throws Exception {
            when(courseRepo.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
            PresignedGetObjectRequest getReq = mock(PresignedGetObjectRequest.class);
            when(getReq.url()).thenReturn(new URL("https://s3.example.com/view"));
            when(s3Service.generatePresignedInlineUrl(anyString(), anyLong())).thenReturn(getReq);

            mockMvc.perform(get("/api/instructor/courses/{c}/resources/presign-view", courseId)
                            .param("s3Key", "courses/doc.pdf"))
                    .andExpect(status().isOk());
        }

        @Test
        void returns403_whenNotOwner() throws Exception {
            when(courseRepo.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(false);

            mockMvc.perform(get("/api/instructor/courses/{c}/resources/presign-view", courseId)
                            .param("s3Key", "courses/doc.pdf"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── GET /{courseId}/resources/presign-download ────────────────────────────

    @Nested
    class PresignDownload {
        @Test
        void returns200_whenOwner() throws Exception {
            when(courseRepo.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
            PresignedGetObjectRequest getReq = mock(PresignedGetObjectRequest.class);
            when(getReq.url()).thenReturn(new URL("https://s3.example.com/dl"));
            when(s3Service.generatePresignedGetUrl(anyString(), anyLong())).thenReturn(getReq);

            mockMvc.perform(get("/api/instructor/courses/{c}/resources/presign-download", courseId)
                            .param("s3Key", "courses/doc.pdf"))
                    .andExpect(status().isOk());
        }

        @Test
        void returns403_whenNotOwner() throws Exception {
            when(courseRepo.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(false);

            mockMvc.perform(get("/api/instructor/courses/{c}/resources/presign-download", courseId)
                            .param("s3Key", "courses/doc.pdf"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── POST /{courseId}/lessons/{lessonId}/resources/presign-upload ──────────

    @Nested
    class RequestUploadUrl {
        @Test
        void returns200_withPresignResponse() throws Exception {
            ResourceUploadPresignResponse presignResp = ResourceUploadPresignResponse.builder()
                    .presignedUrl("https://s3.example.com/upload")
                    .s3Key("courses/file.pdf")
                    .contentType("application/pdf")
                    .build();
            when(lessonResourceService.requestUploadUrl(eq(instructorId), eq(courseId), eq(lessonId), any()))
                    .thenReturn(presignResp);

            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "originalFilename", "lecture.pdf",
                    "mimeType", "application/pdf",
                    "fileSizeBytes", 1024L,
                    "resourceType", "PDF"
            ));

            mockMvc.perform(post("/api/instructor/courses/{c}/lessons/{l}/resources/presign-upload",
                            courseId, lessonId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.s3Key").value("courses/file.pdf"));
        }
    }

    // ── POST /{courseId}/lessons/{lessonId}/resources/confirm-upload ──────────

    @Nested
    class ConfirmUpload {
        @Test
        void returns201_withLessonResource() throws Exception {
            LessonResourceResponse resourceResp = LessonResourceResponse.builder()
                    .id(UUID.randomUUID()).displayName("Lecture PDF").build();
            when(lessonResourceService.confirmUpload(eq(instructorId), eq(courseId), eq(lessonId), any()))
                    .thenReturn(resourceResp);

            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "s3Key", "courses/file.pdf",
                    "resourceType", "PDF"
            ));

            mockMvc.perform(post("/api/instructor/courses/{c}/lessons/{l}/resources/confirm-upload",
                            courseId, lessonId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.displayName").value("Lecture PDF"));
        }
    }

    // ── GET /{courseId}/lessons/{lessonId}/resources ──────────────────────────

    @Nested
    class ListResources {
        @Test
        void returns200_withList() throws Exception {
            LessonResourceResponse r1 = LessonResourceResponse.builder()
                    .id(UUID.randomUUID()).displayName("Slide 1").build();
            LessonResourceResponse r2 = LessonResourceResponse.builder()
                    .id(UUID.randomUUID()).displayName("Slide 2").build();
            when(lessonResourceService.listResources(instructorId, courseId, lessonId))
                    .thenReturn(java.util.List.of(r1, r2));

            mockMvc.perform(get("/api/instructor/courses/{c}/lessons/{l}/resources",
                            courseId, lessonId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void returns200_emptyList() throws Exception {
            when(lessonResourceService.listResources(instructorId, courseId, lessonId))
                    .thenReturn(java.util.List.of());

            mockMvc.perform(get("/api/instructor/courses/{c}/lessons/{l}/resources",
                            courseId, lessonId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ── GET /{courseId}/lessons/{lessonId}/resources/{resourceId}/view-url ────

    @Nested
    class GetViewUrl {
        @Test
        void returns200_withViewUrl() throws Exception {
            UUID resourceId = UUID.randomUUID();
            ResourceDownloadUrlResponse resp = ResourceDownloadUrlResponse.builder()
                    .url("https://s3.example.com/view").build();
            when(lessonResourceService.getViewUrl(instructorId, courseId, lessonId, resourceId))
                    .thenReturn(resp);

            mockMvc.perform(get("/api/instructor/courses/{c}/lessons/{l}/resources/{r}/view-url",
                            courseId, lessonId, resourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://s3.example.com/view"));
        }
    }

    // ── GET /{courseId}/lessons/{lessonId}/resources/{resourceId}/download-url ─

    @Nested
    class GetDownloadUrl {
        @Test
        void returns200_withDownloadUrl() throws Exception {
            UUID resourceId = UUID.randomUUID();
            ResourceDownloadUrlResponse resp = ResourceDownloadUrlResponse.builder()
                    .url("https://s3.example.com/dl").build();
            when(lessonResourceService.getDownloadUrl(instructorId, courseId, lessonId, resourceId))
                    .thenReturn(resp);

            mockMvc.perform(get("/api/instructor/courses/{c}/lessons/{l}/resources/{r}/download-url",
                            courseId, lessonId, resourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://s3.example.com/dl"));
        }
    }

    // ── DELETE /{courseId}/lessons/{lessonId}/resources/{resourceId} ──────────

    @Nested
    class DeleteResource {
        @Test
        void returns204_whenDeleted() throws Exception {
            UUID resourceId = UUID.randomUUID();
            doNothing().when(lessonResourceService)
                    .deleteResource(instructorId, courseId, lessonId, resourceId);

            mockMvc.perform(delete("/api/instructor/courses/{c}/lessons/{l}/resources/{r}",
                            courseId, lessonId, resourceId))
                    .andExpect(status().isNoContent());
        }
    }

    // ── PATCH /{courseId}/lessons/{lessonId}/resources/{resourceId} ───────────

    @Nested
    class RenameResource {
        @Test
        void returns200_withRenamedResource() throws Exception {
            UUID resourceId = UUID.randomUUID();
            LessonResourceResponse resp = LessonResourceResponse.builder()
                    .id(resourceId).displayName("New Name").build();
            when(lessonResourceService.renameResource(
                    eq(instructorId), eq(courseId), eq(lessonId), eq(resourceId), eq("New Name")))
                    .thenReturn(resp);

            mockMvc.perform(patch("/api/instructor/courses/{c}/lessons/{l}/resources/{r}",
                            courseId, lessonId, resourceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"New Name\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("New Name"));
        }
    }

    // ── GET /categories ───────────────────────────────────────────────────────

    @Nested
    class GetCategories {
        @Test
        void returns200_withActiveCategories() throws Exception {
            project.lms_rikkei_edu.modules.course.entity.CourseCategory active =
                    new project.lms_rikkei_edu.modules.course.entity.CourseCategory();
            active.setId(UUID.randomUUID());
            active.setName("Programming");
            active.setIsActive(true);

            project.lms_rikkei_edu.modules.course.entity.CourseCategory inactive =
                    new project.lms_rikkei_edu.modules.course.entity.CourseCategory();
            inactive.setId(UUID.randomUUID());
            inactive.setName("Deprecated");
            inactive.setIsActive(false);

            when(catRepo.findAll()).thenReturn(java.util.List.of(active, inactive));

            mockMvc.perform(get("/api/instructor/courses/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Programming"));
        }

        @Test
        void returns200_emptyList_whenNoCategoriesActive() throws Exception {
            when(catRepo.findAll()).thenReturn(java.util.List.of());

            mockMvc.perform(get("/api/instructor/courses/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ── GET /{courseId}/versions/has-pending ──────────────────────────────────

    @Nested
    class HasPendingVersion {
        @Test
        void returns200_true_whenPendingExists() throws Exception {
            when(courseService.hasPendingVersion(instructorId, courseId)).thenReturn(true);

            mockMvc.perform(get("/api/instructor/courses/{c}/versions/has-pending", courseId))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        void returns200_false_whenNoPending() throws Exception {
            when(courseService.hasPendingVersion(instructorId, courseId)).thenReturn(false);

            mockMvc.perform(get("/api/instructor/courses/{c}/versions/has-pending", courseId))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
    }

    // ── POST /{courseId}/versions/{versionId}/clone-as-draft ─────────────────

    @Nested
    class CloneVersionAsDraft {
        @Test
        void returns200_withClonedVersion() throws Exception {
            CourseVersionResponse cloned = CourseVersionResponse.builder()
                    .id(versionId).status("DRAFT").build();
            when(courseService.cloneVersionAsDraft(instructorId, courseId, versionId, "My Clone"))
                    .thenReturn(cloned);

            mockMvc.perform(post("/api/instructor/courses/{c}/versions/{v}/clone-as-draft",
                            courseId, versionId)
                            .param("label", "My Clone"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void returns200_withoutLabel() throws Exception {
            CourseVersionResponse cloned = CourseVersionResponse.builder()
                    .id(versionId).status("DRAFT").build();
            when(courseService.cloneVersionAsDraft(instructorId, courseId, versionId, null))
                    .thenReturn(cloned);

            mockMvc.perform(post("/api/instructor/courses/{c}/versions/{v}/clone-as-draft",
                            courseId, versionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }
    }
}
