package project.lms_rikkei_edu.modules.ai.exception;

import java.util.UUID;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(UUID id) {
        super("Conversation not found: " + id);
    }
}
