package project.lms_rikkei_edu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    // Expose com.fasterxml.jackson (2.x) ObjectMapper as a bean.
    // Spring Boot 4.x chuyển sang tools.jackson (3.x) nên không auto-configure bean này.
    // Dùng cho các service cần serialize Map/List đơn giản (autosave, import preview).
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
