package project.lms_rikkei_edu.modules.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.ai.entity.AiSource;
import project.lms_rikkei_edu.modules.ai.entity.enums.IngestStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiSourceRepository extends JpaRepository<AiSource, UUID> {

    List<AiSource> findByCourseIdAndDeletedAtIsNull(UUID courseId);

    /** Every active source in the system — every course's docs plus system-wide (courseId=null) docs. */
    List<AiSource> findByDeletedAtIsNull();

    /** Active sources across a set of courses — used to list all docs an instructor's courses have. */
    List<AiSource> findByCourseIdInAndDeletedAtIsNull(Collection<UUID> courseIds);

    List<AiSource> findByCourseIdAndIngestStatusAndDeletedAtIsNull(UUID courseId, IngestStatus ingestStatus);

    List<AiSource> findByLessonIdAndDeletedAtIsNull(UUID lessonId);

    List<AiSource> findByResourceIdAndDeletedAtIsNull(UUID resourceId);
}
