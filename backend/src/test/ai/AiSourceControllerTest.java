package project.lms_rikkei_edu.modules.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.ai.controller.AiSourceController;
import project.lms_rikkei_edu.modules.ai.dto.request.AddFromResourcesRequest;
import project.lms_rikkei_edu.modules.ai.dto.request.SourceIngestRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.AvailableResourceResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.ChunkResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceViewResponse;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.service.AiSourceService;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiSourceControllerTest {

    private AiSourceService sourceService;
    private CurrentUserProvider currentUserProvider;
    private CourseRepository courseRepository;
    private S3Service s3Service;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID courseId     = UUID.randomUUID();
    private final UUID sourceId     = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sourceService       = mock(AiSourceService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        courseRepository    = mock(CourseRepository.class);
        s3Service            = mock(S3Service.class);

        UserPrincipal instructor = mock(UserPrincipal.class);
        when(instructor.getId()).thenReturn(instructorId);
        when(instructor.getRole()).thenReturn(UserRole.INSTRUCTOR);
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(instructor));
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiSourceController(sourceService, currentUserProvider, courseRepository, s3Service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private SourceResponse sampleResponse() {
        return new SourceResponse(sourceId, courseId, "Course", instructorId, SourceType.TEXT, "Source",
                IngestStatus.INDEXED, 5, null, OffsetDateTime.now(), OffsetDateTime.now(), null);
    }

    // ── POST /api/ai/sources ──────────────────────────────────────────────────

    @Nested
    class Ingest {

        @Test
        void returns201_whenIngested() throws Exception {
            UUID spoofedUploadedBy = UUID.randomUUID();
            SourceIngestRequest req = new SourceIngestRequest(
                    courseId, spoofedUploadedBy, SourceType.TEXT,
                    "My Source", "Hello world", null, null);

            when(sourceService.ingest(any())).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/ai/sources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(sourceId.toString()));

            verify(sourceService).ingest(argThat(actual -> actual.uploadedBy().equals(instructorId)));
        }

        @Test
        void returns403_whenInstructorDoesNotOwnCourse() throws Exception {
            UUID otherCourseId = UUID.randomUUID();
            SourceIngestRequest req = new SourceIngestRequest(
                    otherCourseId, null, SourceType.TEXT, "My Source", "Hello world", null, null);

            mockMvc.perform(post("/api/ai/sources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());

            verify(sourceService, never()).ingest(any());
        }

        @Test
        void returns403_whenInstructorOmitsCourseId() throws Exception {
            // courseId is nullable now (system-wide docs), but only ADMIN may create one.
            String body = "{\"uploadedBy\":\"" + UUID.randomUUID() + "\",\"sourceType\":\"TEXT\",\"message\":\"Hi\"}";

            mockMvc.perform(post("/api/ai/sources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        void returns201_whenAdminCreatesSystemWideSource() throws Exception {
            UserPrincipal admin = mock(UserPrincipal.class);
            when(admin.getId()).thenReturn(UUID.randomUUID());
            when(admin.getRole()).thenReturn(UserRole.ADMIN);
            when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(admin));
            SourceIngestRequest req = new SourceIngestRequest(
                    null, null, SourceType.TEXT, "System doc", "Hello world", null, null);
            when(sourceService.ingest(any())).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/ai/sources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            verify(sourceService).ingest(argThat(actual -> actual.courseId() == null));
        }
    }

    // ── GET /api/ai/sources ───────────────────────────────────────────────────

    @Nested
    class List_ {

        @Test
        void returns200_withSources() throws Exception {
            when(sourceService.listByCourse(courseId)).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/ai/sources")
                            .param("courseId", courseId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(sourceId.toString()));
        }

        @Test
        void returns403_whenInstructorDoesNotOwnCourse() throws Exception {
            UUID otherCourseId = UUID.randomUUID();

            mockMvc.perform(get("/api/ai/sources")
                            .param("courseId", otherCourseId.toString()))
                    .andExpect(status().isForbidden());

            verify(sourceService, never()).listByCourse(any());
        }

        @Test
        void returns200_withOwnCoursesDocs_whenInstructorOmitsCourseId() throws Exception {
            when(sourceService.listByInstructor(instructorId)).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/ai/sources"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(sourceId.toString()));

            verify(sourceService, never()).listAll();
        }

        @Test
        void returns200_withAllSources_whenAdminOmitsCourseId() throws Exception {
            UserPrincipal admin = mock(UserPrincipal.class);
            when(admin.getRole()).thenReturn(UserRole.ADMIN);
            when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(admin));
            when(sourceService.listAll()).thenReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/ai/sources"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(sourceId.toString()));
        }
    }

    // ── POST /api/ai/sources/presign-upload ───────────────────────────────────

    @Nested
    class PresignUpload {

        @Test
        void returns200_withUploadUrlAndS3Key() throws Exception {
            PresignedPutObjectRequest presignedPut = mock(PresignedPutObjectRequest.class);
            when(presignedPut.url()).thenReturn(new URL("https://s3.example.com/upload"));
            when(s3Service.generatePresignedPutUrl(any(), any(), anyLong())).thenReturn(presignedPut);

            String body = """
                    {"courseId":"%s","originalFilename":"syllabus.pdf","mimeType":"application/pdf"}
                    """.formatted(courseId);

            mockMvc.perform(post("/api/ai/sources/presign-upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadUrl").value("https://s3.example.com/upload"))
                    .andExpect(jsonPath("$.s3Key", containsString("syllabus.pdf")));
        }

        @Test
        void returns403_whenInstructorDoesNotOwnCourse() throws Exception {
            UUID otherCourseId = UUID.randomUUID();
            String body = """
                    {"courseId":"%s","originalFilename":"syllabus.pdf","mimeType":"application/pdf"}
                    """.formatted(otherCourseId);

            mockMvc.perform(post("/api/ai/sources/presign-upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());

            verify(s3Service, never()).generatePresignedPutUrl(any(), any(), anyLong());
        }
    }

    // ── GET /api/ai/sources/{id} ──────────────────────────────────────────────

    @Nested
    class Get {

        @Test
        void returns200_whenFound() throws Exception {
            when(sourceService.getById(sourceId)).thenReturn(sampleResponse());

            mockMvc.perform(get("/api/ai/sources/{id}", sourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sourceName").value("Source"));
        }

        @Test
        void returns404_whenNotFound() throws Exception {
            when(sourceService.getById(sourceId))
                    .thenThrow(new AiSourceNotFoundException(sourceId));

            mockMvc.perform(get("/api/ai/sources/{id}", sourceId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /api/ai/sources/{id} ───────────────────────────────────────────

    @Nested
    class Delete {

        @Test
        void returns204_whenDeleted() throws Exception {
            when(sourceService.getById(sourceId)).thenReturn(sampleResponse());
            doNothing().when(sourceService).delete(sourceId);

            mockMvc.perform(delete("/api/ai/sources/{id}", sourceId))
                    .andExpect(status().isNoContent());

            verify(sourceService).delete(sourceId);
        }
    }

    // ── POST /api/ai/sources/{id}/reingest ────────────────────────────────────

    @Nested
    class Reingest {

        @Test
        void returns200_whenReingested() throws Exception {
            when(sourceService.getById(sourceId)).thenReturn(sampleResponse());
            when(sourceService.reingest(sourceId)).thenReturn(sampleResponse());

            mockMvc.perform(post("/api/ai/sources/{id}/reingest", sourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ingestStatus").value("INDEXED"));
        }
    }

    // ── GET /api/ai/sources/{id}/view-url ─────────────────────────────────────

    @Nested
    class ViewUrl {

        @Test
        void returns200_withUrl() throws Exception {
            when(sourceService.getById(sourceId)).thenReturn(sampleResponse());
            when(sourceService.getViewUrl(sourceId)).thenReturn(new SourceViewResponse("https://s3.example.com/view"));

            mockMvc.perform(get("/api/ai/sources/{id}/view-url", sourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://s3.example.com/view"));
        }

        @Test
        void returns403_whenInstructorDoesNotOwnCourse() throws Exception {
            UUID otherCourseId = UUID.randomUUID();
            when(sourceService.getById(sourceId)).thenReturn(new SourceResponse(sourceId, otherCourseId, "Other Course",
                    UUID.randomUUID(), SourceType.PDF, "Source", IngestStatus.INDEXED, 5, null,
                    OffsetDateTime.now(), OffsetDateTime.now(), null));

            mockMvc.perform(get("/api/ai/sources/{id}/view-url", sourceId))
                    .andExpect(status().isForbidden());

            verify(sourceService, never()).getViewUrl(any());
        }
    }

    // ── GET /api/ai/sources/{id}/chunks ───────────────────────────────────────

    @Nested
    class Chunks {

        @Test
        void returns200_withChunks() throws Exception {
            when(sourceService.getById(sourceId)).thenReturn(sampleResponse());
            when(sourceService.getChunks(sourceId)).thenReturn(List.of(
                    new ChunkResponse(0, "Chương 1", "Nội dung 1")));

            mockMvc.perform(get("/api/ai/sources/{id}/chunks", sourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].chunkText").value("Nội dung 1"))
                    .andExpect(jsonPath("$[0].sectionTitle").value("Chương 1"));
        }

        @Test
        void returns403_whenInstructorDoesNotOwnCourse() throws Exception {
            UUID otherCourseId = UUID.randomUUID();
            when(sourceService.getById(sourceId)).thenReturn(new SourceResponse(sourceId, otherCourseId, "Other Course",
                    UUID.randomUUID(), SourceType.PDF, "Source", IngestStatus.INDEXED, 5, null,
                    OffsetDateTime.now(), OffsetDateTime.now(), null));

            mockMvc.perform(get("/api/ai/sources/{id}/chunks", sourceId))
                    .andExpect(status().isForbidden());

            verify(sourceService, never()).getChunks(any());
        }
    }

    // ── GET /api/ai/sources/available-resources ───────────────────────────────

    @Nested
    class AvailableResources {

        @Test
        void returns200_withResources() throws Exception {
            UUID resourceId = UUID.randomUUID();
            when(sourceService.listAvailableResources(courseId)).thenReturn(List.of(
                    new AvailableResourceResponse(resourceId, UUID.randomUUID(), "Bài 1", "Chương 1",
                            "Slide.pdf", "application/pdf", false, null)));

            mockMvc.perform(get("/api/ai/sources/available-resources")
                            .param("courseId", courseId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].resourceId").value(resourceId.toString()))
                    .andExpect(jsonPath("$[0].alreadyAdded").value(false));
        }

        @Test
        void returns403_whenInstructorDoesNotOwnCourse() throws Exception {
            UUID otherCourseId = UUID.randomUUID();

            mockMvc.perform(get("/api/ai/sources/available-resources")
                            .param("courseId", otherCourseId.toString()))
                    .andExpect(status().isForbidden());

            verify(sourceService, never()).listAvailableResources(any());
        }
    }

    // ── POST /api/ai/sources/from-resources ────────────────────────────────────

    @Nested
    class AddFromResources {

        @Test
        void returns201_whenAdded() throws Exception {
            UUID resourceId = UUID.randomUUID();
            AddFromResourcesRequest req = new AddFromResourcesRequest(courseId, List.of(resourceId));
            when(sourceService.ingestFromResources(courseId, List.of(resourceId)))
                    .thenReturn(List.of(sampleResponse()));

            mockMvc.perform(post("/api/ai/sources/from-resources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0].id").value(sourceId.toString()));
        }

        @Test
        void returns403_whenInstructorDoesNotOwnCourse() throws Exception {
            UUID otherCourseId = UUID.randomUUID();
            AddFromResourcesRequest req = new AddFromResourcesRequest(otherCourseId, List.of(UUID.randomUUID()));

            mockMvc.perform(post("/api/ai/sources/from-resources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());

            verify(sourceService, never()).ingestFromResources(any(), any());
        }
    }
}
