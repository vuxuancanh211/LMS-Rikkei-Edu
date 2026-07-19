package project.lms_rikkei_edu.modules.ai.exception;

import java.util.UUID;

public class IngestionException extends RuntimeException {
    public IngestionException(UUID sourceId, String reason) {
        super("Ingestion failed for source " + sourceId + ": " + reason);
    }

    public IngestionException(String reason) {
        super(reason);
    }
}
