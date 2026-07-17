package project.lms_rikkei_edu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("email-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Pool riêng, CỐ Ý nhỏ (3 luồng) cho việc gọi LLM song song theo lô khi sinh câu hỏi AI
     * ({@code AiQuestionGeneratorServiceImpl}). Mục đích chỉ là giảm thời gian chờ TUẦN TỰ
     * giữa các lô (mỗi lô vẫn gọi OpenAI 1 lần), KHÔNG phải tăng thông lượng vô hạn — giữ pool
     * nhỏ để tránh: (1) dồn dập request tới OpenAI gây rate-limit, (2) tốn quá nhiều thread/heap
     * của server khi nhiều giảng viên sinh câu hỏi cùng lúc. queueCapacity giới hạn cứng để job
     * dư ra phải chờ thay vì xếp hàng vô hạn.
     */
    @Bean(name = "aiGenerationExecutor")
    public Executor aiGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-gen-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
