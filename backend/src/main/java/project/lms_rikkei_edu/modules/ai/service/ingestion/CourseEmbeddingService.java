package project.lms_rikkei_edu.modules.ai.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;
import project.lms_rikkei_edu.modules.ai.entity.enums.SourceType;
import project.lms_rikkei_edu.modules.ai.repository.AiSourceRepository;
import project.lms_rikkei_edu.modules.ai.repository.DocumentChunkRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseEmbeddingService {

    private final AiSourceRepository sourceRepo;
    private final DocumentChunkRepository chunkRepo;
    private final IngestionOrchestrator orchestrator;
    private final JdbcTemplate jdbc;

    /**
     * Embed toàn bộ nội dung khóa học khi được approve lần đầu.
     * Chạy async — không block API response.
     */
    @Async
    public void embedCourseAsync(UUID courseId) {
        log.info("Starting course embedding: courseId={}", courseId);

        List<LessonTextRow> lessons = jdbc.query(
                "SELECT id, title, content_text FROM lessons WHERE course_id = ? AND content_text IS NOT NULL AND content_text <> ''",
                (rs, i) -> new LessonTextRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("content_text")
                ),
                courseId
        );

        List<ResourceRow> resources = jdbc.query(
                "SELECT id, s3_key, mime_type, display_name FROM lesson_resources WHERE course_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL",
                (rs, i) -> new ResourceRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("s3_key"),
                        rs.getString("mime_type"),
                        rs.getString("display_name")
                ),
                courseId
        );

        log.info("courseId={} → {} lessons, {} resources to embed", courseId, lessons.size(), resources.size());

        for (LessonTextRow lesson : lessons) {
            embedLesson(courseId, lesson.id(), lesson.title(), lesson.contentText());
        }

        for (ResourceRow res : resources) {
            embedResource(courseId, res.id(), res.s3Key(), res.mimeType(), res.displayName());
        }
    }

    /**
     * Re-embed một lesson khi instructor cập nhật content_text (sau khi admin approve update).
     */
    @Async
    public void reembedLessonAsync(UUID courseId, UUID lessonId, String title, String contentText) {
        deleteChunksByLessonId(lessonId);
        embedLesson(courseId, lessonId, title, contentText);
    }

    /**
     * Embed một resource mới vừa upload (sau khi admin approve update).
     */
    @Async
    public void embedResourceAsync(UUID courseId, UUID resourceId, String s3Key, String mimeType, String displayName) {
        embedResource(courseId, resourceId, s3Key, mimeType, displayName);
    }

    /**
     * Xóa toàn bộ chunks và sources khi resource bị xóa.
     */
    @Transactional
    public void deleteEmbeddingsByResource(UUID resourceId) {
        List<AiSource> sources = sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId);
        for (AiSource source : sources) {
            chunkRepo.deleteBySourceId(source.getId());
            source.setDeletedAt(OffsetDateTime.now());
            source.setStatus("DELETED");
            sourceRepo.save(source);
        }
        log.info("Deleted embeddings for resourceId={}", resourceId);
    }

    /**
     * Xóa toàn bộ chunks và sources cho một course (khi unpublish).
     */
    @Transactional
    public void deleteAllEmbeddingsByCourse(UUID courseId) {
        List<AiSource> sources = sourceRepo.findByCourseIdAndDeletedAtIsNull(courseId);
        for (AiSource source : sources) {
            chunkRepo.deleteBySourceId(source.getId());
            source.setDeletedAt(OffsetDateTime.now());
            source.setStatus("DELETED");
            sourceRepo.save(source);
        }
        log.info("Deleted all embeddings for courseId={}", courseId);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void embedLesson(UUID courseId, UUID lessonId, String title, String contentText) {
        AiSource existing = sourceRepo.findByLessonIdAndDeletedAtIsNull(lessonId).stream().findFirst().orElse(null);

        AiSource source;
        if (existing != null) {
            chunkRepo.deleteBySourceId(existing.getId());
            source = existing;
            source.setIngestStatus(IngestStatus.PENDING);
            source.setChunkCount(null);
            source.setErrorMessage(null);
        } else {
            source = AiSource.builder()
                    .courseId(courseId)
                    .lessonId(lessonId)
                    .sourceType(SourceType.TEXT)
                    .sourceName(title)
                    .sourceUrl(contentText)
                    .status("ACTIVE")
                    .ingestStatus(IngestStatus.PENDING)
                    .createdAt(OffsetDateTime.now())
                    .build();
        }
        source = sourceRepo.save(source);
        orchestrator.ingest(source.getId());
    }

    /** Embeds one resource synchronously — reused by both the auto-embed async flow and manual instructor selection. */
    public void embedResource(UUID courseId, UUID resourceId, String s3Key, String mimeType, String displayName) {
        SourceType type = SourceType.fromMimeType(mimeType);
        if (type == null) {
            log.debug("Skipping resource {} — unsupported MIME type: {}", resourceId, mimeType);
            return;
        }

        AiSource existing = sourceRepo.findByResourceIdAndDeletedAtIsNull(resourceId).stream().findFirst().orElse(null);

        AiSource source;
        if (existing != null) {
            chunkRepo.deleteBySourceId(existing.getId());
            source = existing;
            source.setIngestStatus(IngestStatus.PENDING);
            source.setChunkCount(null);
            source.setErrorMessage(null);
            source.setExternalId(s3Key);
        } else {
            source = AiSource.builder()
                    .courseId(courseId)
                    .resourceId(resourceId)
                    .sourceType(type)
                    .sourceName(displayName)
                    .externalId(s3Key)
                    .status("ACTIVE")
                    .ingestStatus(IngestStatus.PENDING)
                    .createdAt(OffsetDateTime.now())
                    .build();
        }
        source = sourceRepo.save(source);
        orchestrator.ingest(source.getId());
    }

    private void deleteChunksByLessonId(UUID lessonId) {
        sourceRepo.findByLessonIdAndDeletedAtIsNull(lessonId)
                .forEach(s -> chunkRepo.deleteBySourceId(s.getId()));
    }

    record LessonTextRow(UUID id, String title, String contentText) {}
    record ResourceRow(UUID id, String s3Key, String mimeType, String displayName) {}
}
