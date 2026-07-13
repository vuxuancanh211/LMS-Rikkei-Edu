package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;
import project.lms_rikkei_edu.modules.ai.service.embedding.OpenAiEmbeddingService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiEmbeddingServiceTest {

    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private OpenAiEmbeddingService service;

    @BeforeEach
    void setUp() {
        RestClient restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        service = new OpenAiEmbeddingService(restClient, new OpenAiProperties());
    }

    @Test
    void embed_returnsFirstVectorOfSingleTextBatch() {
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "data", List.of(Map.of("index", 0, "embedding", List.of(0.1, 0.2, 0.3)))));

        float[] vec = service.embed("hello world");

        assertThat(vec).containsExactly(0.1f, 0.2f, 0.3f);
        verify(uriSpec).uri("/embeddings");
    }

    @Test
    void embedBatch_sortsResultsByIndex_regardlessOfResponseOrder() {
        // OpenAI's response can arrive out of order; the service must re-sort by "index".
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "data", List.of(
                        Map.of("index", 1, "embedding", List.of(2.0, 2.0)),
                        Map.of("index", 0, "embedding", List.of(1.0, 1.0)))));

        List<float[]> vectors = service.embedBatch(List.of("a", "b"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 1.0f);
        assertThat(vectors.get(1)).containsExactly(2.0f, 2.0f);
    }

    @Test
    void embedBatch_sendsModelAndDimensionFromProperties() {
        when(responseSpec.body(Map.class)).thenReturn(Map.of(
                "data", List.of(Map.of("index", 0, "embedding", List.of(0.5)))));

        service.embedBatch(List.of("text"));

        verify(bodySpec).body(org.mockito.ArgumentMatchers.argThat((Object body) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) body;
            return "text-embedding-3-small".equals(map.get("model"))
                    && Integer.valueOf(1024).equals(map.get("dimensions"));
        }));
    }
}
