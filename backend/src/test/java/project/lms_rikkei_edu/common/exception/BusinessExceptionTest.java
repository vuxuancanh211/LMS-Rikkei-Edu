package project.lms_rikkei_edu.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void createWithMessage() {
        BusinessException ex = new BusinessException("Something went wrong");

        assertThat(ex.getMessage()).isEqualTo("Something went wrong");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void createWithMessageAndStatus() {
        BusinessException ex = new BusinessException("Not found", HttpStatus.NOT_FOUND);

        assertThat(ex.getMessage()).isEqualTo("Not found");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
