package project.lms_rikkei_edu.modules.ai.dto.response;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        UUID messageId,
        String answer,
        List<SourceReference> sources,
        int totalTokens,
        StructuredData structuredData,
        UiRender uiRender
) {}
