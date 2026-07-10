package project.lms_rikkei_edu.modules.ai.service.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import project.lms_rikkei_edu.modules.ai.config.OpenAiProperties;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiEmbeddingService implements EmbeddingService {

    @Qualifier("openAiRestClient")
    private final RestClient restClient;
    private final OpenAiProperties props;

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<float[]> embedBatch(List<String> texts) {
        var body = Map.of(
                "model", props.getEmbeddingModel(),
                "input", texts,
                "dimensions", props.getEmbeddingDimension()
        );

        Map<String, Object> response = restClient.post()
                .uri("/embeddings")
                .body(body)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

        return data.stream()
                .sorted((a, b) -> Integer.compare((int) a.get("index"), (int) b.get("index")))
                .map(item -> {
                    List<Number> raw = (List<Number>) item.get("embedding");
                    float[] vec = new float[raw.size()];
                    for (int i = 0; i < raw.size(); i++) vec[i] = raw.get(i).floatValue();
                    return vec;
                })
                .toList();
    }
}
