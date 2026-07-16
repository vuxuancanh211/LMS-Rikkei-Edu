package project.lms_rikkei_edu.modules.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.course.dto.request.CreateChapterRequest;
import project.lms_rikkei_edu.modules.course.dto.request.CreateCourseRequest;
import project.lms_rikkei_edu.modules.course.dto.request.ReorderRequest;
import project.lms_rikkei_edu.modules.course.dto.request.UpdateCourseRequest;
import project.lms_rikkei_edu.modules.course.dto.response.*;
import project.lms_rikkei_edu.modules.course.enums.CourseLevel;
import project.lms_rikkei_edu.modules.course.enums.CourseStatus;
import project.lms_rikkei_edu.modules.course.exception.*;
import project.lms_rikkei_edu.modules.course.repository.CourseCategoryRepository;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.course.service.CourseService;
import project.lms_rikkei_edu.modules.course.service.LessonResourceService;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseControllerTest {

    private CourseService courseService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private S3Service s3Service;
    private CourseRepository courseRepo;

    private final UUID instructorId = UUID.randomUUID();
    private final UUID courseId     = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        courseService          = mock(CourseService.class);
        currentUserProvider    = mock(CurrentUserProvider.class);
        LessonResourceService lessonResourceService = mock(LessonResourceService.class);
        s3Service               = mock(S3Service.class);
        CourseCategoryRepository catRepo = mock(CourseCategoryRepository.class);
        courseRepo              = mock(CourseRepository.class);

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

    private CourseResponse courseResponse(UUID id, String title, CourseStatus status) {
        return CourseResponse.builder()
                .id(id)
                .title(title)
                .status(status)
                .level(CourseLevel.BEGINNER)
                .build();
    }

    private CourseDetailResponse detailResponse(UUID id) {
        return CourseDetailResponse.builder()
                .id(id)
                .title("Test Course")
                .status(CourseStatus.DRAFT)
                .chapters(List.of())
                .build();
    }

    // ── POST /api/instructor/courses ──────────────────────────────────────────

    @Nested
    class CreateCourse {

        @Test
        void returns201WithCourseResponse() throws Exception {
            CreateCourseRequest req = new CreateCourseRequest();
            req.setTitle("Spring Boot Course");

            CourseResponse response = courseResponse(courseId, "Spring Boot Course", CourseStatus.DRAFT);
            when(courseService.createCourse(eq(instructorId), any(CreateCourseRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/instructor/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(courseId.toString()))
                    .andExpect(jsonPath("$.title").value("Spring Boot Course"))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void returns400_whenTitleBlank() throws Exception {
            CreateCourseRequest req = new CreateCourseRequest();
            req.setTitle("");

            mockMvc.perform(post("/api/instructor/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns400_whenTitleTooShort() throws Exception {
            CreateCourseRequest req = new CreateCourseRequest();
            req.setTitle("Hi"); // < 5 chars

            mockMvc.perform(post("/api/instructor/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/instructor/courses ───────────────────────────────────────────

    @Nested
    class ListCourses {

        @Test
        void returnsPagedList() throws Exception {
            CourseResponse c1 = courseResponse(courseId, "Course 1", CourseStatus.DRAFT);
            CourseResponse c2 = courseResponse(UUID.randomUUID(), "Course 2", CourseStatus.PUBLISHED);

            when(courseService.listCourses(eq(instructorId), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(c1, c2), PageRequest.of(0, 20), 2));

            mockMvc.perform(get("/api/instructor/courses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].title").value("Course 1"))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void returnsEmptyPage_whenNoCoursesExist() throws Exception {
            when(courseService.listCourses(eq(instructorId), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/api/instructor/courses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // ── GET /api/instructor/courses/by-slug/{slug} ────────────────────────────

    @Nested
    class GetCourseDetailBySlug {

        @Test
        void returns200WithDetail() throws Exception {
            when(courseService.getCourseDetailBySlug(instructorId, "java-boot"))
                    .thenReturn(detailResponse(courseId));

            mockMvc.perform(get("/api/instructor/courses/by-slug/{slug}", "java-boot"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(courseId.toString()));
        }

        @Test
        void returns404_whenCourseNotFound() throws Exception {
            when(courseService.getCourseDetailBySlug(instructorId, "missing-slug"))
                    .thenThrow(new CourseNotFoundException(courseId));

            mockMvc.perform(get("/api/instructor/courses/by-slug/{slug}", "missing-slug"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/instructor/courses/{courseId} ────────────────────────────────

    @Nested
    class GetCourseDetail {

        @Test
        void returns200WithDetail() throws Exception {
            when(courseService.getCourseDetail(instructorId, courseId))
                    .thenReturn(detailResponse(courseId));

            mockMvc.perform(get("/api/instructor/courses/{id}", courseId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(courseId.toString()))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void returns404_whenCourseNotFound() throws Exception {
            when(courseService.getCourseDetail(instructorId, courseId))
                    .thenThrow(new CourseNotFoundException(courseId));

            mockMvc.perform(get("/api/instructor/courses/{id}", courseId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── PUT /api/instructor/courses/{courseId} ────────────────────────────────

    @Nested
    class UpdateCourse {

        @Test
        void returns200WithUpdatedCourse() throws Exception {
            UpdateCourseRequest req = new UpdateCourseRequest();
            req.setTitle("Updated Title");

            CourseResponse updated = courseResponse(courseId, "Updated Title", CourseStatus.DRAFT);
            when(courseService.updateCourse(eq(instructorId), eq(courseId), any(UpdateCourseRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/instructor/courses/{id}", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated Title"));
        }

        @Test
        void returns409_whenCourseArchived() throws Exception {
            UpdateCourseRequest req = new UpdateCourseRequest();
            req.setTitle("New Title");

            when(courseService.updateCourse(eq(instructorId), eq(courseId), any()))
                    .thenThrow(new CourseStateException("Cannot modify an archived course"));

            mockMvc.perform(put("/api/instructor/courses/{id}", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict());
        }
    }

    // ── DELETE /api/instructor/courses/{courseId} ─────────────────────────────

    @Nested
    class DeleteCourse {

        @Test
        void returns204_whenDeleted() throws Exception {
            doNothing().when(courseService).deleteCourse(instructorId, courseId);

            mockMvc.perform(delete("/api/instructor/courses/{id}", courseId))
                    .andExpect(status().isNoContent());

            verify(courseService).deleteCourse(instructorId, courseId);
        }

        @Test
        void returns409_whenPublishedCourse() throws Exception {
            doThrow(new CourseStateException("Cannot delete a published course"))
                    .when(courseService).deleteCourse(instructorId, courseId);

            mockMvc.perform(delete("/api/instructor/courses/{id}", courseId))
                    .andExpect(status().isConflict());
        }
    }

    // ── PUT /api/instructor/courses/{courseId}/submit ─────────────────────────

    @Nested
    class SubmitForApproval {

        @Test
        void returns200_whenSubmitted() throws Exception {
            when(courseService.submitForApproval(eq(instructorId), eq(courseId), any()))
                    .thenReturn(detailResponse(courseId));

            mockMvc.perform(put("/api/instructor/courses/{id}/submit", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(courseId.toString()));
        }

        @Test
        void returns409_whenNoLessons() throws Exception {
            when(courseService.submitForApproval(eq(instructorId), eq(courseId), any()))
                    .thenThrow(new CourseStateException("Course must have at least one lesson"));

            mockMvc.perform(put("/api/instructor/courses/{id}/submit", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict());
        }
    }

    // ── PUT /api/instructor/courses/{courseId}/withdraw ───────────────────────

    @Nested
    class WithdrawFromReview {

        @Test
        void returns200_whenWithdrawn() throws Exception {
            when(courseService.withdrawFromReview(instructorId, courseId))
                    .thenReturn(detailResponse(courseId));

            mockMvc.perform(put("/api/instructor/courses/{id}/withdraw", courseId))
                    .andExpect(status().isOk());
        }

        @Test
        void returns409_whenNoPendingReview() throws Exception {
            when(courseService.withdrawFromReview(instructorId, courseId))
                    .thenThrow(new CourseStateException("No pending draft to withdraw"));

            mockMvc.perform(put("/api/instructor/courses/{id}/withdraw", courseId))
                    .andExpect(status().isConflict());
        }
    }

    // ── POST /api/instructor/courses/{courseId}/chapters ──────────────────────

    @Nested
    class AddChapter {

        @Test
        void returns201WithChapter() throws Exception {
            CreateChapterRequest req = new CreateChapterRequest();
            req.setTitle("Chapter 1");

            ChapterResponse chapter = ChapterResponse.builder()
                    .id(UUID.randomUUID())
                    .title("Chapter 1")
                    .orderIndex(1)
                    .build();

            when(courseService.addChapter(eq(instructorId), eq(courseId), any(CreateChapterRequest.class)))
                    .thenReturn(chapter);

            mockMvc.perform(post("/api/instructor/courses/{id}/chapters", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Chapter 1"))
                    .andExpect(jsonPath("$.orderIndex").value(1));
        }
    }

    // ── DELETE /api/instructor/courses/{courseId}/chapters/{chapterId} ─────────

    @Nested
    class DeleteChapter {

        @Test
        void returns204_whenDeleted() throws Exception {
            UUID chapterId = UUID.randomUUID();
            doNothing().when(courseService).deleteChapter(instructorId, courseId, chapterId);

            mockMvc.perform(delete("/api/instructor/courses/{cId}/chapters/{chId}", courseId, chapterId))
                    .andExpect(status().isNoContent());
        }

        @Test
        void returns404_whenChapterNotFound() throws Exception {
            UUID chapterId = UUID.randomUUID();
            doThrow(new ChapterNotFoundException(chapterId))
                    .when(courseService).deleteChapter(instructorId, courseId, chapterId);

            mockMvc.perform(delete("/api/instructor/courses/{cId}/chapters/{chId}", courseId, chapterId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── PUT /api/instructor/courses/{courseId}/chapters/reorder ────────────────

    @Nested
    class ReorderChapters {

        @Test
        void returnsReorderedChapters() throws Exception {
            UUID chapterId1 = UUID.randomUUID();
            UUID chapterId2 = UUID.randomUUID();
            ReorderRequest request = new ReorderRequest();
            request.setIds(List.of(chapterId1, chapterId2));

            List<ChapterResponse> reordered = List.of(
                    ChapterResponse.builder().id(chapterId1).orderIndex(0).build(),
                    ChapterResponse.builder().id(chapterId2).orderIndex(1).build());
            when(courseService.reorderChapters(instructorId, courseId, request.getIds()))
                    .thenReturn(reordered);

            mockMvc.perform(put("/api/instructor/courses/{courseId}/chapters/reorder", courseId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(chapterId1.toString()));
        }
    }

    // ── PUT /api/instructor/courses/{courseId}/chapters/{chapterId}/lessons/reorder ──

    @Nested
    class ReorderLessons {

        @Test
        void returnsReorderedLessons() throws Exception {
            UUID chapterId = UUID.randomUUID();
            UUID lessonId1 = UUID.randomUUID();
            UUID lessonId2 = UUID.randomUUID();
            ReorderRequest request = new ReorderRequest();
            request.setIds(List.of(lessonId1, lessonId2));

            List<LessonResponse> reordered = List.of(
                    LessonResponse.builder().id(lessonId1).orderIndex(0).build(),
                    LessonResponse.builder().id(lessonId2).orderIndex(1).build());
            when(courseService.reorderLessons(instructorId, courseId, chapterId, request.getIds()))
                    .thenReturn(reordered);

            mockMvc.perform(put("/api/instructor/courses/{courseId}/chapters/{chapterId}/lessons/reorder",
                            courseId, chapterId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(lessonId1.toString()));
        }
    }

    // ── GET /api/instructor/courses/{courseId}/resources/presign-view ──
    // ── GET /api/instructor/courses/{courseId}/resources/presign-download ──

    @Nested
    class PresignViewResource {

        @Test
        void returnsPresignedUrl_whenOwnerCourse() throws Exception {
            when(courseRepo.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
            software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest presigned =
                    mock(software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(new java.net.URI("https://s3.example.com/preview.pdf").toURL());
            when(s3Service.generatePresignedInlineUrl("courses/preview.pdf", 3600L)).thenReturn(presigned);

            mockMvc.perform(get("/api/instructor/courses/{courseId}/resources/presign-view", courseId)
                            .param("s3Key", "courses/preview.pdf"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://s3.example.com/preview.pdf"));
        }

        @Test
        void returnsForbidden_whenNotOwner() throws Exception {
            when(courseRepo.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(false);

            mockMvc.perform(get("/api/instructor/courses/{courseId}/resources/presign-view", courseId)
                            .param("s3Key", "courses/preview.pdf"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(s3Service);
        }
    }

    @Nested
    class PresignDownloadResource {

        @Test
        void returnsPresignedUrl_whenOwnerCourse() throws Exception {
            when(courseRepo.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);
            software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest presigned =
                    mock(software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(new java.net.URI("https://s3.example.com/download.pdf").toURL());
            when(s3Service.generatePresignedGetUrl("courses/download.pdf", 3600L)).thenReturn(presigned);

            mockMvc.perform(get("/api/instructor/courses/{courseId}/resources/presign-download", courseId)
                            .param("s3Key", "courses/download.pdf"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://s3.example.com/download.pdf"));
        }

        @Test
        void returnsForbidden_whenNotOwner() throws Exception {
            when(courseRepo.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(false);

            mockMvc.perform(get("/api/instructor/courses/{courseId}/resources/presign-download", courseId)
                            .param("s3Key", "courses/download.pdf"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(s3Service);
        }
    }
}
