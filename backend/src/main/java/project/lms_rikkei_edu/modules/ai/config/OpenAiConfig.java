package project.lms_rikkei_edu.modules.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {

    @Bean("openAiRestClient")
    public RestClient openAiRestClient(OpenAiProperties props) {
        // Không có timeout thì 1 request treo (mạng lag, OpenAI chậm) sẽ chặn vô thời hạn —
        // đặc biệt rủi ro từ khi sinh câu hỏi AI gọi thêm 1 lượt embedding (RAG) TRƯỚC lượt
        // gọi LLM chính, cộng dồn có thể vượt timeout 120s phía frontend.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(45));

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(requestFactory)
                .build();
    }
}
