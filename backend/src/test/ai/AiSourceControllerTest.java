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
import project.lms_rikkei_edu.modules.ai.dto.request.SourceIngestRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
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
        return new SourceResponse(sourceId, courseId, SourceType.TEXT, "Source",
                IngestStatus.INDEXED, 5, null, OffsetDateTime.now(), OffsetDateTime.now());
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
        void returns400_whenCourseIdNull() throws Exception {
            String body = "{\"uploadedBy\":\"" + UUID.randomUUID() + "\",\"sourceType\":\"TEXT\",\"message\":\"Hi\"}";

            mockMvc.perform(post("/api/ai/sources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
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
}
