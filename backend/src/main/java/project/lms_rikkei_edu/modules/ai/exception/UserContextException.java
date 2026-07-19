package project.lms_rikkei_edu.modules.ai.exception;

import java.util.UUID;

public class UserContextException extends RuntimeException {
    public UserContextException(UUID userId) {
        super("User not found: " + userId);
    }
}
