package project.lms_rikkei_edu.modules.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.ai.service.llm.ChatMessage;
import project.lms_rikkei_edu.modules.ai.service.llm.LlmResponse;
import project.lms_rikkei_edu.modules.ai.service.llm.OpenAiLlmService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiLlmServiceTest {

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private OpenAiLlmService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        // body() is overloaded (Object / StreamingHttpOutputMessage.Body) — untyped any() binds to
        // the wrong overload, so pin the matcher to Object.class to match the production call site.
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        service = new OpenAiLlmService(restClient, new OpenAiProperties(), new ObjectMapper());
    }

    private Map<String, Object> chatResponse(String content, List<Map<String, Object>> toolCalls,
                                              int promptTokens, int completionTokens, int totalTokens) {
        Map<String, Object> message = new HashMap<>();
        message.put("content", content);
        if (toolCalls != null) message.put("tool_calls", toolCalls);
        Map<String, Object> choice = Map.of("message", message);
        Map<String, Object> usage = Map.of(
                "prompt_tokens", promptTokens, "completion_tokens", completionTokens, "total_tokens", totalTokens);
        return Map.of("choices", List.of(choice), "usage", usage);
    }

    @Test
    void plainAnswer_noToolCall_returnsContentDirectly() {
        when(responseSpec.body(Map.class)).thenReturn(chatResponse("Xin chào!", null, 10, 5, 15));

        LlmResponse resp = service.complete("system prompt", List.of(), "Hi");

        assertThat(resp.content()).isEqualTo("Xin chào!");
        assertThat(resp.promptTokens()).isEqualTo(10);
        assertThat(resp.completionTokens()).isEqualTo(5);
        assertThat(resp.totalTokens()).isEqualTo(15);
        assertThat(resp.uiRender()).isNull();
        verify(uriSpec, times(1)).uri("/chat/completions");
    }

    @Test
    void toolCallWithContent_setsUiRenderWithoutExtraCall() throws Exception {
        List<Map<String, Object>> toolCalls = List.of(Map.of(
                "id", "call_1",
                "function", Map.of("name", "render_list", "arguments", "{\"items\":[\"A\",\"B\"]}")));
        when(responseSpec.body(Map.class)).thenReturn(chatResponse("Đây là danh sách", toolCalls, 20, 8, 28));

        LlmResponse resp = service.complete("system prompt", List.of(), "Liệt kê");

        assertThat(resp.content()).isEqualTo("Đây là danh sách");
        assertThat(resp.uiRender()).isNotNull();
        assertThat(resp.uiRender().component()).isEqualTo("list");
        assertThat(resp.uiRender().props()).containsKey("items");
        verify(uriSpec, times(1)).uri("/chat/completions");
    }

    @Test
    void toolCallWithoutContent_triggersFollowUpCallAndSumsUsage() {
        List<Map<String, Object>> toolCalls = List.of(Map.of(
                "id", "call_1",
                "function", Map.of("name", "render_table",
                        "arguments", "{\"columns\":[\"A\"],\"rows\":[]}")));
        Map<String, Object> firstResp = chatResponse("", toolCalls, 20, 0, 20);
        Map<String, Object> followUpResp = chatResponse("Đây là bảng dữ liệu", null, 25, 10, 35);
        when(responseSpec.body(Map.class)).thenReturn(firstResp, followUpResp);

        LlmResponse resp = service.complete("system prompt", List.of(), "Cho tôi bảng");

        assertThat(resp.content()).isEqualTo("Đây là bảng dữ liệu");
        assertThat(resp.uiRender().component()).isEqualTo("table");
        assertThat(resp.promptTokens()).isEqualTo(45);
        assertThat(resp.completionTokens()).isEqualTo(10);
        assertThat(resp.totalTokens()).isEqualTo(55);
        verify(uriSpec, times(2)).uri("/chat/completions");
    }

    @Test
    @SuppressWarnings("unchecked")
    void history_isIncludedInRequestMessages() {
        when(responseSpec.body(Map.class)).thenReturn(chatResponse("OK", null, 1, 1, 2));

        service.complete("system", List.of(ChatMessage.user("Trước đó"), ChatMessage.assistant("Đã trả lời")), "Tiếp theo");

        verify(bodySpec).body(org.mockito.ArgumentMatchers.argThat((Object body) -> {
            Map<String, Object> map = (Map<String, Object>) body;
            List<Map<String, Object>> messages = (List<Map<String, Object>>) map.get("messages");
            return messages.stream().anyMatch(m -> "Trước đó".equals(m.get("content")));
        }));
    }

    @Test
    void malformedToolArguments_returnsNullUiRenderGracefully() {
        List<Map<String, Object>> toolCalls = List.of(Map.of(
                "id", "call_1",
                "function", Map.of("name", "render_table", "arguments", "{not valid json")));
        when(responseSpec.body(Map.class)).thenReturn(chatResponse("Vẫn có câu trả lời", toolCalls, 10, 5, 15));

        LlmResponse resp = service.complete("system", List.of(), "Câu hỏi");

        assertThat(resp.uiRender()).isNull();
        assertThat(resp.content()).isEqualTo("Vẫn có câu trả lời");
        // content already non-blank, so no follow-up call is made even without a uiRender.
        verify(uriSpec, times(1)).uri("/chat/completions");
    }
}
