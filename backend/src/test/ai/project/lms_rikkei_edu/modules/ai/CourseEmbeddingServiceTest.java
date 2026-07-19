package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;
import project.lms_rikkei_edu.modules.ai.service.ingestion.CourseEmbeddingService;
import project.lms_rikkei_edu.modules.ai.service.ingestion.IngestionOrchestrator;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseEmbeddingServiceTest {

    @Mock AiSourceRepository sourceRepo;
    @Mock DocumentChunkRepository chunkRepo;
    @Mock IngestionOrchestrator orchestrator;
    @Mock JdbcTemplate jdbc;

    CourseEmbeddingService service;

    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CourseEmbeddingService(sourceRepo, chunkRepo, orchestrator, jdbc);
        when(sourceRepo.save(any())).thenAnswer(inv -> {
            AiSource s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
    }

    // ── embedCourseAsync ─────────────────────────────────────────────────────

    @Nested
    class EmbedCourseAsync {

        @Test
        void embedsAllLessonsAndResourcesFound() throws Exception {
            UUID lessonId = UUID.randomUUID();
            ResultSet lessonRs = mock(ResultSet.class);
            when(lessonRs.getObject("id", UUID.class)).thenReturn(lessonId);
            when(lessonRs.getString("title")).thenReturn("Bài 1");
            when(lessonRs.getString("content_text")).thenReturn("Nội dung bài 1");

            UUID resourceId = UUID.randomUUID();
            ResultSet resRs = mock(ResultSet.class);
            when(resRs.getObject("id", UUID.class)).thenReturn(resourceId);
            when(resRs.getString("s3_key")).thenReturn("courses/slide.pdf");
            when(resRs.getString("mime_type")).thenReturn("application/pdf");
            when(resRs.getString("display_name")).thenReturn("Slide.pdf");

            when(jdbc.query(contains("FROM lessons"), any(RowMapper.class), eq(courseId)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(lessonRs, 0)));
            when(jdbc.query(contains("FROM lesson_resources"), any(RowMapper.class), eq(courseId)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(resRs, 0)));
            when(sourceRepo.findByLessonIdAndDeletedAtIsNull(lessonId)).thenReturn(List.of());
            when(sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId)).thenReturn(List.of());

            service.embedCourseAsync(courseId);

            ArgumentCaptor<AiSource> captor = ArgumentCaptor.forClass(AiSource.class);
            verify(sourceRepo, times(2)).save(captor.capture());
            List<AiSource> saved = captor.getAllValues();
            assertThat(saved).anySatisfy(s -> {
                assertThat(s.getLessonId()).isEqualTo(lessonId);
                assertThat(s.getSourceType()).isEqualTo(SourceType.TEXT);
                assertThat(s.getSourceUrl()).isEqualTo("Nội dung bài 1");
            });
            assertThat(saved).anySatisfy(s -> {
                assertThat(s.getResourceId()).isEqualTo(resourceId);
                assertThat(s.getSourceType()).isEqualTo(SourceType.PDF);
                assertThat(s.getExternalId()).isEqualTo("courses/slide.pdf");
            });
            verify(orchestrator, times(2)).ingest(any());
        }

        @Test
        void noLessonsOrResources_doesNothing() {
            when(jdbc.query(anyString(), any(RowMapper.class), eq(courseId))).thenReturn(List.of());

            service.embedCourseAsync(courseId);

            verify(sourceRepo, never()).save(any());
            verify(orchestrator, never()).ingest(any());
        }
    }

    // ── embedLesson (via embedCourseAsync) ──────────────────────────────────

    @Nested
    class EmbedLesson {

        @Test
        void existingSource_reusesRecordAndClearsOldChunks() throws Exception {
            UUID lessonId = UUID.randomUUID();
            UUID existingSourceId = UUID.randomUUID();
            AiSource existing = AiSource.builder().id(existingSourceId).lessonId(lessonId)
                    .ingestStatus(IngestStatus.INDEXED).chunkCount(5).errorMessage("old error").build();

            ResultSet lessonRs = mock(ResultSet.class);
            when(lessonRs.getObject("id", UUID.class)).thenReturn(lessonId);
            when(lessonRs.getString("title")).thenReturn("Bài 1 (đã sửa)");
            when(lessonRs.getString("content_text")).thenReturn("Nội dung mới");

            when(jdbc.query(contains("FROM lessons"), any(RowMapper.class), eq(courseId)))
                    .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(lessonRs, 0)));
            when(jdbc.query(contains("FROM lesson_resources"), any(RowMapper.class), eq(courseId)))
                    .thenReturn(List.of());
            when(sourceRepo.findByLessonIdAndDeletedAtIsNull(lessonId)).thenReturn(List.of(existing));

            service.embedCourseAsync(courseId);

            verify(chunkRepo).deleteBySourceId(existingSourceId);
            ArgumentCaptor<AiSource> captor = ArgumentCaptor.forClass(AiSource.class);
            verify(sourceRepo).save(captor.capture());
            AiSource saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(existingSourceId);
            assertThat(saved.getIngestStatus()).isEqualTo(IngestStatus.PENDING);
            assertThat(saved.getChunkCount()).isNull();
            assertThat(saved.getErrorMessage()).isNull();
            verify(orchestrator).ingest(existingSourceId);
        }
    }

    // ── embedResource (public, called directly for manual add-from-resource flow) ──

    @Nested
    class EmbedResource {

        @Test
        void unsupportedMimeType_skipsWithoutSaving() {
            service.embedResource(courseId, UUID.randomUUID(), "key", "application/zip", "archive.zip");

            verify(sourceRepo, never()).save(any());
            verify(orchestrator, never()).ingest(any());
        }

        @Test
        void newSource_createsAndIngests() {
            UUID resourceId = UUID.randomUUID();
            when(sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId)).thenReturn(List.of());

            service.embedResource(courseId, resourceId, "courses/doc.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Doc.docx");

            ArgumentCaptor<AiSource> captor = ArgumentCaptor.forClass(AiSource.class);
            verify(sourceRepo).save(captor.capture());
            AiSource saved = captor.getValue();
            assertThat(saved.getCourseId()).isEqualTo(courseId);
            assertThat(saved.getResourceId()).isEqualTo(resourceId);
            assertThat(saved.getSourceType()).isEqualTo(SourceType.DOC);
            assertThat(saved.getExternalId()).isEqualTo("courses/doc.docx");
            assertThat(saved.getSourceName()).isEqualTo("Doc.docx");
            assertThat(saved.getIngestStatus()).isEqualTo(IngestStatus.PENDING);
            verify(orchestrator).ingest(saved.getId());
        }

        @Test
        void existingSource_reusesRecordAndUpdatesS3Key() {
            UUID resourceId = UUID.randomUUID();
            UUID existingSourceId = UUID.randomUUID();
            AiSource existing = AiSource.builder().id(existingSourceId).resourceId(resourceId)
                    .ingestStatus(IngestStatus.FAILED).chunkCount(null).errorMessage("boom")
                    .externalId("old/key.pdf").build();
            when(sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId)).thenReturn(List.of(existing));

            service.embedResource(courseId, resourceId, "new/key.pdf", "application/pdf", "New.pdf");

            verify(chunkRepo).deleteBySourceId(existingSourceId);
            ArgumentCaptor<AiSource> captor = ArgumentCaptor.forClass(AiSource.class);
            verify(sourceRepo).save(captor.capture());
            AiSource saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(existingSourceId);
            assertThat(saved.getExternalId()).isEqualTo("new/key.pdf");
            assertThat(saved.getIngestStatus()).isEqualTo(IngestStatus.PENDING);
            assertThat(saved.getErrorMessage()).isNull();
            verify(orchestrator).ingest(existingSourceId);
        }
    }

    // ── embedResourceAsync / reembedLessonAsync (thin @Async wrappers) ──────

    @Test
    void embedResourceAsync_delegatesToEmbedResource() {
        UUID resourceId = UUID.randomUUID();
        when(sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId)).thenReturn(List.of());

        service.embedResourceAsync(courseId, resourceId, "key.pdf", "application/pdf", "File.pdf");

        verify(sourceRepo).save(any());
        verify(orchestrator).ingest(any());
    }

    @Test
    void reembedLessonAsync_deletesOldChunksThenReembeds() throws Exception {
        UUID lessonId = UUID.randomUUID();
        UUID existingSourceId = UUID.randomUUID();
        AiSource existing = AiSource.builder().id(existingSourceId).lessonId(lessonId)
                .ingestStatus(IngestStatus.INDEXED).build();
        when(sourceRepo.findByLessonIdAndDeletedAtIsNull(lessonId)).thenReturn(List.of(existing));

        service.reembedLessonAsync(courseId, lessonId, "Bài 1", "Nội dung");

        // deleteChunksByLessonId + embedLesson's own reuse-branch both call deleteBySourceId for the same id.
        verify(chunkRepo, atLeastOnce()).deleteBySourceId(existingSourceId);
        verify(sourceRepo).save(any());
        verify(orchestrator).ingest(existingSourceId);
    }

    // ── deleteEmbeddingsByResource / deleteAllEmbeddingsByCourse ────────────

    @Test
    void deleteEmbeddingsByResource_softDeletesAllMatchingSources() {
        UUID resourceId = UUID.randomUUID();
        AiSource s1 = AiSource.builder().id(UUID.randomUUID()).resourceId(resourceId).build();
        AiSource s2 = AiSource.builder().id(UUID.randomUUID()).resourceId(resourceId).build();
        when(sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId)).thenReturn(List.of(s1, s2));

        service.deleteEmbeddingsByResource(resourceId);

        verify(chunkRepo).deleteBySourceId(s1.getId());
        verify(chunkRepo).deleteBySourceId(s2.getId());
        ArgumentCaptor<AiSource> captor = ArgumentCaptor.forClass(AiSource.class);
        verify(sourceRepo, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(s -> {
            assertThat(s.getStatus()).isEqualTo("DELETED");
            assertThat(s.getDeletedAt()).isNotNull();
        });
    }

    @Test
    void deleteAllEmbeddingsByCourse_softDeletesAllCourseSources() {
        AiSource s1 = AiSource.builder().id(UUID.randomUUID()).courseId(courseId).build();
        when(sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId)).thenReturn(List.of(s1));

        service.deleteAllEmbeddingsByCourse(courseId);

        verify(chunkRepo).deleteBySourceId(s1.getId());
        ArgumentCaptor<AiSource> captor = ArgumentCaptor.forClass(AiSource.class);
        verify(sourceRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DELETED");
    }

    @Test
    void deleteAllEmbeddingsByCourse_noSources_doesNothing() {
        when(sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId)).thenReturn(List.of());

        service.deleteAllEmbeddingsByCourse(courseId);

        verify(chunkRepo, never()).deleteBySourceId(any());
        verify(sourceRepo, never()).save(any());
    }
}
