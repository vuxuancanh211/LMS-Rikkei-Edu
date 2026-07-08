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
    private String chatModel = "gpt-4o-mini";
    private double temperature = 0.7;
    private int maxTokens = 1500;

    /** Number of document chunks retrieved per query. */
    private int topK = 5;

    /** Minimum cosine-similarity score (0-1) for a chunk to be included in context. */
    private double similarityThreshold = 0.5;

    /** Maximum recent messages to include in conversation history. */
    private int maxHistoryMessages = 10;
}
