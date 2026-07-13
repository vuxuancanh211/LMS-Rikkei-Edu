package project.lms_rikkei_edu.common.exception;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ErrorResponseTest {

    @Test
    void createErrorResponse() {
        ErrorResponse response = ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message("Invalid input")
                .path("/api/test")
                .timestamp(OffsetDateTime.now())
                .validationErrors(Map.of("field", "must not be null"))
                .build();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("Bad Request");
        assertThat(response.getMessage()).isEqualTo("Invalid input");
        assertThat(response.getPath()).isEqualTo("/api/test");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getValidationErrors()).containsKey("field");
    }

    @Test
    void builderDefaults() {
        ErrorResponse response = ErrorResponse.builder()
                .status(500)
                .error("Internal Server Error")
                .message("Unexpected error")
                .path("/api/test")
                .build();

        assertThat(response.getValidationErrors()).isNull();
        assertThat(response.getTimestamp()).isNull();
    }
}
