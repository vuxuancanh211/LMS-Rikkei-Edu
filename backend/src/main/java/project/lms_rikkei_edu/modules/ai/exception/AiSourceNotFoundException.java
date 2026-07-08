package project.lms_rikkei_edu.modules.ai.exception;

import java.util.UUID;

public class AiSourceNotFoundException extends RuntimeException {
    public AiSourceNotFoundException(UUID id) {
        super("AI source not found: " + id);
    }
}
