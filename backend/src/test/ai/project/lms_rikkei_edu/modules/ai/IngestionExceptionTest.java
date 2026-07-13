package project.lms_rikkei_edu.modules.ai;

import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.ai.exception.IngestionException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionExceptionTest {

    @Test
    void message_includesSourceIdAndReason() {
        UUID sourceId = UUID.randomUUID();

        IngestionException ex = new IngestionException(sourceId, "No text could be extracted");

        assertThat(ex.getMessage()).isEqualTo("Ingestion failed for source " + sourceId + ": No text could be extracted");
    }

    @Test
    void message_reasonOnly() {
        IngestionException ex = new IngestionException("boom");

        assertThat(ex.getMessage()).isEqualTo("boom");
    }
}
