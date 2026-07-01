package project.lms_rikkei_edu.modules.ai.service.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiLlmService implements LlmService {

    @Qualifier("openAiRestClient")
    private final RestClient restClient;
    private final OpenAiProperties props;

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse complete(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatMessage m : history) {
            messages.add(Map.of("role", m.role(), "content", m.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        var body = Map.of(
                "model", props.getChatModel(),
                "messages", messages,
                "temperature", props.getTemperature(),
                "max_tokens", props.getMaxTokens()
        );

        long start = System.currentTimeMillis();

        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);

        long elapsed = System.currentTimeMillis() - start;

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> messageObj = (Map<String, Object>) choices.getFirst().get("message");
        String content = (String) messageObj.get("content");

        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        int promptTokens     = ((Number) usage.get("prompt_tokens")).intValue();
        int completionTokens = ((Number) usage.get("completion_tokens")).intValue();
        int totalTokens      = ((Number) usage.get("total_tokens")).intValue();

        log.debug("LLM response: {} tokens, {}ms", totalTokens, elapsed);

        return new LlmResponse(content, promptTokens, completionTokens, totalTokens, elapsed);
    }
}
