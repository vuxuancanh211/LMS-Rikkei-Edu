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
import project.lms_rikkei_edu.modules.ai.dto.response.SourceResponse;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;
import project.lms_rikkei_edu.modules.ai.service.AiSourceService;
import project.lms_rikkei_edu.modules.ai.service.ingestion.IngestionOrchestrator;

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

    AiSourceService service;

    private final UUID courseId   = UUID.randomUUID();
    private final UUID uploadedBy = UUID.randomUUID();
    private final UUID sourceId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AiSourceService(sourceRepo, chunkRepo, orchestrator);
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
}
