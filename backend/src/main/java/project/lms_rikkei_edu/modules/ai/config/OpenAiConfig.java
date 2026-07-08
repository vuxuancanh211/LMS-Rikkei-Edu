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
        // Không có timeout thì 1 request treo (mạng lag, OpenAI chậm) sẽ chặn vô thời hạn.
        // readTimeout 120s (không còn 45s như trước): sinh câu hỏi AI giờ chạy nền hoàn toàn
        // (job + polling, xem AiQuestionGeneratorService/generateAsync), không còn bị ràng buộc
        // bởi timeout HTTP phía frontend nữa — nên có thể chờ model reasoning (GPT-5 family)
        // sinh xong 1 lô lớn câu hỏi (VD 20 câu) mà không bị hủy request giữa chừng.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(requestFactory)
                .build();
    }
}
