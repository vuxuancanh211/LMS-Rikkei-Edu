package project.lms_rikkei_edu.modules.ai.service.llm;

/** Internal result returned by {@link LlmService}. */
public record LlmResponse(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long responseTimeMs
) {}
