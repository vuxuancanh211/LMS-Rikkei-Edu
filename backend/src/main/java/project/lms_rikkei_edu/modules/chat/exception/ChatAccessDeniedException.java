package project.lms_rikkei_edu.modules.chat.exception;

public class ChatAccessDeniedException extends RuntimeException {
    public ChatAccessDeniedException(String message) {
        super(message);
    }
}
