package project.lms_rikkei_edu.modules.ai.dto.response;

import java.util.UUID;

/** A lesson resource (PDF/DOC) eligible to be added to a course's AI knowledge base. */
public record AvailableResourceResponse(
        UUID resourceId,
        UUID lessonId,
        String lessonTitle,
        String chapterTitle,
        String displayName,
        String mimeType,
        boolean alreadyAdded,
        UUID aiSourceId
) {}
