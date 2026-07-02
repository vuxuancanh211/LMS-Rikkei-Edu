package project.lms_rikkei_edu.modules.chat.exception;

import java.util.UUID;

public class ChatMessageNotFoundException extends RuntimeException {
    public ChatMessageNotFoundException(UUID messageId) {
        super("Không tìm thấy tin nhắn: " + messageId);
    }
}
