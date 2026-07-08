package project.lms_rikkei_edu.modules.ai.service.llm;

/** A single turn in the message list sent to the LLM. */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) { return new ChatMessage("system", content); }
    public static ChatMessage user(String content)   { return new ChatMessage("user", content); }
    public static ChatMessage assistant(String content) { return new ChatMessage("assistant", content); }
}
