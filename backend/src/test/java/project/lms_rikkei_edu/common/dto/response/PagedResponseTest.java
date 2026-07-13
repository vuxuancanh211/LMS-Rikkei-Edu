package project.lms_rikkei_edu.common.dto.response;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PagedResponseTest {

    @Test
    void calculatesTotalPagesCorrectly() {
        List<String> items = Arrays.asList("a", "b", "c");
        PagedResponse<String> response = new PagedResponse<>(items, 10, 1, 3);

        assertThat(response.getItems()).hasSize(3);
        assertThat(response.getTotalRecords()).isEqualTo(10);
        assertThat(response.getTotalPages()).isEqualTo(4);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(3);
    }

    @Test
    void handlesExactDivision() {
        PagedResponse<String> response = new PagedResponse<>(Collections.emptyList(), 9, 0, 3);

        assertThat(response.getTotalPages()).isEqualTo(3);
    }

    @Test
    void handlesZeroRecords() {
        PagedResponse<String> response = new PagedResponse<>(Collections.emptyList(), 0, 0, 10);

        assertThat(response.getTotalPages()).isEqualTo(0);
    }

    @Test
    void handlesNullItems() {
        PagedResponse<String> response = new PagedResponse<>(null, 5, 0, 10);

        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void throwsWhenSizeIsZero() {
        assertThatThrownBy(() -> new PagedResponse<>(Collections.emptyList(), 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be positive");
    }

    @Test
    void throwsWhenSizeIsNegative() {
        assertThatThrownBy(() -> new PagedResponse<>(Collections.emptyList(), 0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be positive");
    }

    @Test
    void normalizesNegativePage() {
        PagedResponse<String> response = new PagedResponse<>(Collections.emptyList(), 0, -5, 10);

        assertThat(response.getPage()).isEqualTo(0);
    }

    @Test
    void handlesNegativeTotalRecords() {
        PagedResponse<String> response = new PagedResponse<>(Collections.emptyList(), -10, 0, 10);

        assertThat(response.getTotalRecords()).isEqualTo(0);
    }
}
