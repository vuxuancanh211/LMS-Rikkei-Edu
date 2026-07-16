package project.lms_rikkei_edu.modules.forum.repository;

import java.util.UUID;

public interface ForumReportCountProjection {
    UUID getTargetId();

    long getTotalCount();

    long getPendingCount();
}
