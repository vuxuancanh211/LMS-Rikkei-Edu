package project.lms_rikkei_edu.modules.ai.service.llm;

import java.util.List;

/**
 * Abstraction over a chat-completion language model.
 * Swap implementations to change LLM provider without touching business logic.
 */
public interface LlmService {

    /**
     * Complete a conversation.
     *
     * @param systemPrompt initial system instruction (may include RAG context)
     * @param history      prior conversation turns (user/assistant pairs), oldest first
     * @param userMessage  the new user question
     * @return LLM reply with token usage and latency
     */
    LlmResponse complete(String systemPrompt, List<ChatMessage> history, String userMessage);
}
