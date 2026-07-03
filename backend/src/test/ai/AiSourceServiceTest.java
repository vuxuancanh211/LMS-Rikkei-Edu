package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import project.lms_rikkei_edu.modules.ai.dto.request.SourceIngestRequest;
import project.lms_rikkei_edu.modules.ai.dto.response.AvailableResourceResponse;
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;
import project.lms_rikkei_edu.modules.ai.service.AiSourceService;
import project.lms_rikkei_edu.modules.ai.service.ingestion.CourseEmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.ingestion.IngestionOrchestrator;
import project.lms_rikkei_edu.modules.course.entity.Chapter;
import project.lms_rikkei_edu.modules.course.entity.Lesson;
import project.lms_rikkei_edu.modules.course.entity.LessonResource;
import project.lms_rikkei_edu.modules.course.enums.ResourceType;
import project.lms_rikkei_edu.modules.course.repository.LessonResourceRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiSourceServiceTest {

    @Mock AiSourceRepository sourceRepo;
    @Mock DocumentChunkRepository chunkRepo;
    @Mock IngestionOrchestrator orchestrator;
    @Mock LessonResourceRepository lessonResourceRepo;
    @Mock CourseEmbeddingService courseEmbeddingService;

    AiSourceService service;

    private final UUID courseId   = UUID.randomUUID();
    private final UUID uploadedBy = UUID.randomUUID();
    private final UUID sourceId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AiSourceService(sourceRepo, chunkRepo, orchestrator, lessonResourceRepo, courseEmbeddingService);
    }

    private AiSource buildSource(UUID id) {
        return AiSource.builder()
                .id(id)
                .courseId(courseId)
                .uploadedBy(uploadedBy)
                .sourceType(SourceType.TEXT)
                .sourceName("Test Source")
                .status("ACTIVE")
                .ingestStatus(IngestStatus.INDEXED)
                .chunkCount(5)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // ── ingest ────────────────────────────────────────────────────────────────

    @Nested
    class Ingest {

        @Test
        void savesSourceAndRunsIngestion() {
            SourceIngestRequest req = new SourceIngestRequest(
                    courseId, uploadedBy, SourceType.TEXT,
                    "My Source", "Hello world content", null, null);

            AiSource saved  = buildSource(sourceId);
            AiSource reloaded = buildSource(sourceId);

            when(sourceRepo.save(any())).thenReturn(saved);
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(reloaded));

            SourceResponse result = service.ingest(req);

            verify(sourceRepo).save(any(AiSource.class));
            verify(orchestrator).ingest(sourceId);
            assertThat(result.id()).isEqualTo(sourceId);
            assertThat(result.ingestStatus()).isEqualTo(IngestStatus.INDEXED);
        }

        @Test
        void copiesMetadataIntoSource() {
            SourceIngestRequest req = new SourceIngestRequest(
                    courseId, uploadedBy, SourceType.PDF,
                    "PDF Source", null, null, java.util.Map.of("s3Key", "courses/doc.pdf"));

            AiSource saved = buildSource(sourceId);
            when(sourceRepo.save(any())).thenReturn(saved);
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(saved));

            service.ingest(req);

            verify(sourceRepo, atLeastOnce()).save(any(AiSource.class));
        }

        @Test
        void mapsMetadataS3KeyToExternalId_soPdfHandlerCanFindIt() {
            SourceIngestRequest req = new SourceIngestRequest(
                    courseId, uploadedBy, SourceType.PDF,
                    "PDF Source", null, null, java.util.Map.of("s3Key", "courses/doc.pdf"));

            AiSource saved = buildSource(sourceId);
            when(sourceRepo.save(any())).thenReturn(saved);
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(saved));

            service.ingest(req);

            org.mockito.ArgumentCaptor<AiSource> captor = org.mockito.ArgumentCaptor.forClass(AiSource.class);
            verify(sourceRepo).save(captor.capture());
            assertThat(captor.getValue().getExternalId()).isEqualTo("courses/doc.pdf");
        }
    }

    // ── listByCourse ──────────────────────────────────────────────────────────

    @Nested
    class ListByCourse {

        @Test
        void returnsAllActiveSources() {
            when(sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId))
                    .thenReturn(List.of(buildSource(UUID.randomUUID()), buildSource(UUID.randomUUID())));

            List<SourceResponse> list = service.listByCourse(courseId);

            assertThat(list).hasSize(2);
        }

        @Test
        void returnsEmptyList_whenNoSources() {
            when(sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId)).thenReturn(List.of());

            assertThat(service.listByCourse(courseId)).isEmpty();
        }
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Nested
    class GetById {

        @Test
        void returnsSource_whenFound() {
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(buildSource(sourceId)));

            SourceResponse resp = service.getById(sourceId);

            assertThat(resp.id()).isEqualTo(sourceId);
        }

        @Test
        void throws_whenNotFound() {
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(sourceId))
                    .isInstanceOf(AiSourceNotFoundException.class);
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested
    class Delete {

        @Test
        void softDeletesSourceAndRemovesChunks() {
            AiSource source = buildSource(sourceId);
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));

            service.delete(sourceId);

            verify(chunkRepo).deleteBySourceId(sourceId);
            verify(sourceRepo, atLeastOnce()).save(source);
            assertThat(source.getStatus()).isEqualTo("DELETED");
            assertThat(source.getDeletedAt()).isNotNull();
        }

        @Test
        void throws_whenSourceNotFound() {
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(sourceId))
                    .isInstanceOf(AiSourceNotFoundException.class);
        }
    }

    // ── reingest ──────────────────────────────────────────────────────────────

    @Nested
    class Reingest {

        @Test
        void reingests_andReturnsUpdatedSource() {
            AiSource source = buildSource(sourceId);
            when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));

            SourceResponse resp = service.reingest(sourceId);

            verify(orchestrator).reingest(sourceId);
            assertThat(resp.id()).isEqualTo(sourceId);
        }
    }

    // ── listAvailableResources ───────────────────────────────────────────────

    @Nested
    class ListAvailableResources {

        private LessonResource buildResource(UUID resourceId, String mimeType) {
            Chapter chapter = Chapter.builder().id(UUID.randomUUID()).title("Chương 1").build();
            Lesson lesson = Lesson.builder().id(UUID.randomUUID()).chapter(chapter).title("Bài 1").build();
            return LessonResource.builder()
                    .id(resourceId)
                    .lesson(lesson)
                    .courseId(courseId)
                    .displayName("Slide.pdf")
                    .s3Key("courses/slide.pdf")
                    .mimeType(mimeType)
                    .resourceType(ResourceType.PDF)
                    .build();
        }

        @Test
        void marksAlreadyAdded_whenAiSourceExistsForResource() {
            UUID resourceId = UUID.randomUUID();
            when(lessonResourceRepo.findAllByCourseIdWithLessonAndChapter(courseId))
                    .thenReturn(List.of(buildResource(resourceId, "application/pdf")));
            AiSource existing = buildSource(UUID.randomUUID());
            existing.setResourceId(resourceId);
            when(sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId)).thenReturn(List.of(existing));

            List<AvailableResourceResponse> result = service.listAvailableResources(courseId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).alreadyAdded()).isTrue();
            assertThat(result.get(0).aiSourceId()).isEqualTo(existing.getId());
        }

        @Test
        void excludesUnsupportedMimeTypes() {
            LessonResource videoResource = buildResource(UUID.randomUUID(), "video/mp4");
            videoResource.setResourceType(ResourceType.VIDEO);
            when(lessonResourceRepo.findAllByCourseIdWithLessonAndChapter(courseId))
                    .thenReturn(List.of(videoResource));
            when(sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId)).thenReturn(List.of());

            assertThat(service.listAvailableResources(courseId)).isEmpty();
        }

        @Test
        void includesResource_whenMimeTypeBlankButResourceTypeIsDoc() {
            // Regression: some legacy lesson_resources rows have a NULL mime_type even though
            // resourceType (DOC/PDF) is set correctly — must not be silently excluded.
            LessonResource resource = buildResource(UUID.randomUUID(), null);
            resource.setResourceType(ResourceType.DOC);
            when(lessonResourceRepo.findAllByCourseIdWithLessonAndChapter(courseId)).thenReturn(List.of(resource));
            when(sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId)).thenReturn(List.of());

            assertThat(service.listAvailableResources(courseId)).hasSize(1);
        }
    }

    // ── ingestFromResources ──────────────────────────────────────────────────

    @Nested
    class IngestFromResources {

        @Test
        void embedsEachResource_andReturnsCreatedSources() {
            UUID resourceId = UUID.randomUUID();
            Chapter chapter = Chapter.builder().id(UUID.randomUUID()).title("Chương 1").build();
            Lesson lesson = Lesson.builder().id(UUID.randomUUID()).chapter(chapter).title("Bài 1").build();
            LessonResource resource = LessonResource.builder()
                    .id(resourceId).lesson(lesson).courseId(courseId)
                    .displayName("Slide.pdf").s3Key("courses/slide.pdf")
                    .mimeType("application/pdf").resourceType(ResourceType.PDF)
                    .build();
            when(lessonResourceRepo.findById(resourceId)).thenReturn(Optional.of(resource));

            AiSource created = buildSource(UUID.randomUUID());
            created.setResourceId(resourceId);
            when(sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId)).thenReturn(List.of(created));

            List<SourceResponse> result = service.ingestFromResources(courseId, List.of(resourceId));

            verify(courseEmbeddingService).embedResource(courseId, resourceId, "courses/slide.pdf", "application/pdf", "Slide.pdf");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(created.getId());
        }

        @Test
        void throws_whenResourceBelongsToDifferentCourse() {
            UUID resourceId = UUID.randomUUID();
            LessonResource resource = LessonResource.builder()
                    .id(resourceId).courseId(UUID.randomUUID())
                    .mimeType("application/pdf")
                    .build();
            when(lessonResourceRepo.findById(resourceId)).thenReturn(Optional.of(resource));

            assertThatThrownBy(() -> service.ingestFromResources(courseId, List.of(resourceId)))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(courseEmbeddingService, never()).embedResource(any(), any(), any(), any(), any());
        }

        @Test
        void embedsResource_whenMimeTypeBlankButResourceTypeIsDoc() {
            UUID resourceId = UUID.randomUUID();
            LessonResource resource = LessonResource.builder()
                    .id(resourceId).courseId(courseId)
                    .displayName("Slide.docx").s3Key("courses/slide.docx")
                    .mimeType(null).resourceType(ResourceType.DOC)
                    .build();
            when(lessonResourceRepo.findById(resourceId)).thenReturn(Optional.of(resource));
            AiSource created = buildSource(UUID.randomUUID());
            when(sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId)).thenReturn(List.of(created));

            service.ingestFromResources(courseId, List.of(resourceId));

            verify(courseEmbeddingService).embedResource(courseId, resourceId, "courses/slide.docx", "application/msword", "Slide.docx");
        }

        @Test
        void throws_whenMimeTypeUnsupported() {
            UUID resourceId = UUID.randomUUID();
            LessonResource resource = LessonResource.builder()
                    .id(resourceId).courseId(courseId)
                    .mimeType("video/mp4")
                    .build();
            when(lessonResourceRepo.findById(resourceId)).thenReturn(Optional.of(resource));

            assertThatThrownBy(() -> service.ingestFromResources(courseId, List.of(resourceId)))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(courseEmbeddingService, never()).embedResource(any(), any(), any(), any(), any());
        }
    }
}
