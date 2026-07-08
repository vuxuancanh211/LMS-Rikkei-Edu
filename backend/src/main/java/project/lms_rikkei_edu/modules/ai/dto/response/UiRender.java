package project.lms_rikkei_edu.modules.ai.dto.response;

import java.util.Map;

/**
 * Generic UI rendering directive chosen by the LLM itself (via function
 * calling — see {@code OpenAiLlmService}), as opposed to {@link StructuredData}
 * which is attached by a fixed backend heuristic. {@code component} is one of
 * the tool names the LLM was offered ("table", "stat_cards", "progress_bars",
 * "list"); {@code props} is whatever that tool's arguments were, copied
 * through unchanged for the frontend to render.
 */
public record UiRender(String component, Map<String, Object> props) {}
