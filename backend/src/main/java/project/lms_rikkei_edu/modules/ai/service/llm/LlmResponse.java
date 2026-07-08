package project.lms_rikkei_edu.modules.ai.service.llm;

import project.lms_rikkei_edu.modules.ai.dto.response.UiRender;

/** Internal result returned by {@link LlmService}. */
public record LlmResponse(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long responseTimeMs,
        UiRender uiRender
) {}
