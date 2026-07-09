package project.lms_rikkei_edu.modules.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
@Data
public class OpenAiProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";

    /** OpenAI embedding model; must produce exactly embeddingDimension floats. */
    private String embeddingModel = "text-embedding-3-small";
    private int embeddingDimension = 1024;

    /** Chat-completion model used by the RAG pipeline. */
    private String chatModel = "gpt-5-mini";
    private double temperature = 0.7;
    /**
     * Với dòng model GPT-5 (reasoning model), giá trị này là
     * {@code max_completion_tokens} —
     * NGÂN SÁCH CHUNG cho cả reasoning token (ẩn) lẫn output thấy được. Đặt quá
     * thấp (VD 1500)
     * khiến model tiêu hết ngân sách vào reasoning và trả về content RỖNG khi
     * output cần dài
     * — không phải lỗi, chỉ là hết ngân sách token. Dùng cho luồng chat/RAG bình
     * thường.
     */
    private int maxTokens = 4096;

    /**
     * Ngân sách token riêng cho
     * {@link project.lms_rikkei_edu.modules.ai.service.llm.LlmService#completeForJson}
     * — cao hơn hẳn {@link #maxTokens} vì output là danh sách nhiều câu hỏi trắc
     * nghiệm
     * (VD 20 câu × 4-5 đáp án × giải thích mỗi đáp án có thể lên tới hàng nghìn
     * token JSON),
     * tách riêng khỏi ngân sách chat thường để không đội chi phí/độ trễ cho các câu
     * hỏi
     * chatbot đơn giản.
     */
    private int jsonMaxTokens = 8000;

    /** Number of document chunks retrieved per query. */
    private int topK = 5;

    /**
     * Minimum cosine-similarity score (0-1) for a chunk to be included in context.
     */
    private double similarityThreshold = 0.5;

    /** Maximum recent messages to include in conversation history. */
    private int maxHistoryMessages = 10;
}
