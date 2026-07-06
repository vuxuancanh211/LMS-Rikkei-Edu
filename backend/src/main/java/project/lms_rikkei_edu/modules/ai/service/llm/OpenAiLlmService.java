package project.lms_rikkei_edu.modules.ai.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.ai.dto.response.UiRender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI chat completions, optionally letting the model itself pick a
 * UI widget to render alongside its text answer (see {@link #RENDER_TOOLS}).
 *
 * <p>Tool-calling flow: OpenAI sometimes returns a tool call with an empty
 * {@code content} (the model "spent" that turn deciding to call the tool
 * instead of writing prose). When that happens we make one extra
 * follow-up call, feeding back a synthetic tool result, purely to get the
 * natural-language answer to go with the widget. Plain questions that don't
 * trigger a tool call still cost exactly one API call, same as before.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiLlmService implements LlmService {

    @Qualifier("openAiRestClient")
    private final RestClient restClient;
    private final OpenAiProperties props;
    private final ObjectMapper objectMapper;

    /**
     * Fixed set of generic UI widgets the model may choose to fill in,
     * instead of us hand-detecting "this question needs a table" per case.
     * The model must only use data already present in the conversation —
     * enforced via the system prompt, not by this schema.
     */
    private static final List<Object> RENDER_TOOLS = parseTools("""
            [
              {
                "type": "function",
                "function": {
                  "name": "render_table",
                  "description": "Show a small table alongside your answer, for tabular data already given to you in the context.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "title": { "type": "string" },
                      "columns": { "type": "array", "items": { "type": "string" } },
                      "rows": { "type": "array", "items": { "type": "array", "items": { "type": "string" } } }
                    },
                    "required": ["columns", "rows"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "render_stat_cards",
                  "description": "Show a row of small stat cards (label + value), for a handful of key numbers already given to you in the context.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "items": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": { "label": { "type": "string" }, "value": { "type": "string" } },
                          "required": ["label", "value"]
                        }
                      }
                    },
                    "required": ["items"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "render_progress_bars",
                  "description": "Show labeled progress bars, for percentage/completion data already given to you in the context.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "items": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": { "label": { "type": "string" }, "percent": { "type": "number" } },
                          "required": ["label", "percent"]
                        }
                      }
                    },
                    "required": ["items"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "render_list",
                  "description": "Show a simple bullet list, for a short list of names/items already given to you in the context.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "title": { "type": "string" },
                      "items": { "type": "array", "items": { "type": "string" } }
                    },
                    "required": ["items"]
                  }
                }
              }
            ]
            """);

    private static List<Object> parseTools(String json) {
        try {
            return new ObjectMapper().readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid RENDER_TOOLS schema", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse complete(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatMessage m : history) {
            messages.add(Map.of("role", m.role(), "content", m.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        long start = System.currentTimeMillis();

        Map<String, Object> response = callChatCompletions(messages, true);
        Map<String, Object> messageObj = firstMessage(response);
        String content = (String) messageObj.get("content");
        UiRender uiRender = extractUiRender(messageObj);

        int[] usage = extractUsage(response);

        if ((content == null || content.isBlank()) && uiRender != null) {
            Object toolCallId = ((List<Map<String, Object>>) messageObj.get("tool_calls")).get(0).get("id");
            messages.add(messageObj);
            messages.add(Map.of("role", "tool", "tool_call_id", toolCallId, "content", "{\"status\":\"rendered\"}"));

            Map<String, Object> followUp = callChatCompletions(messages, false);
            Map<String, Object> followMessage = firstMessage(followUp);
            content = (String) followMessage.get("content");

            int[] followUsage = extractUsage(followUp);
            usage[0] += followUsage[0];
            usage[1] += followUsage[1];
            usage[2] += followUsage[2];
        }

        long elapsed = System.currentTimeMillis() - start;
        log.debug("LLM response: {} tokens, {}ms, uiRender={}", usage[2], elapsed, uiRender != null ? uiRender.component() : null);

        return new LlmResponse(content, usage[0], usage[1], usage[2], elapsed, uiRender);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String completeForJson(String systemPrompt, String userMessage) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userMessage)
        );
        Map<String, Object> body = new HashMap<>();
        body.put("model",           props.getChatModel());
        body.put("messages",        messages);
        body.put("temperature",     props.getTemperature());
        body.put("max_tokens",      props.getMaxTokens());
        body.put("response_format", Map.of("type", "json_object"));
        Map<String, Object> response = restClient.post()
                .uri("/chat/completions").body(body).retrieve()
                .body(Map.class);
        return (String) firstMessage(response).get("content");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callChatCompletions(List<Map<String, Object>> messages, boolean includeTools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getChatModel());
        body.put("messages", messages);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        if (includeTools) {
            body.put("tools", RENDER_TOOLS);
            body.put("tool_choice", "auto");
        }
        return restClient.post().uri("/chat/completions").body(body).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMessage(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        return (Map<String, Object>) choices.getFirst().get("message");
    }

    @SuppressWarnings("unchecked")
    private int[] extractUsage(Map<String, Object> response) {
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        return new int[]{
                ((Number) usage.get("prompt_tokens")).intValue(),
                ((Number) usage.get("completion_tokens")).intValue(),
                ((Number) usage.get("total_tokens")).intValue()
        };
    }

    @SuppressWarnings("unchecked")
    private UiRender extractUiRender(Map<String, Object> messageObj) {
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) messageObj.get("tool_calls");
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
        String name = (String) function.get("name");
        String argsJson = (String) function.get("arguments");
        try {
            Map<String, Object> props = objectMapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {});
            String component = name.startsWith("render_") ? name.substring("render_".length()) : name;
            return new UiRender(component, props);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tool call arguments for {}: {}", name, argsJson, e);
            return null;
        }
    }
}
