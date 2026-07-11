package project.lms_rikkei_edu.modules.course.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReorderRequestTest {

    @Test
    void gettersAndSetters_roundTrip() {
        ReorderRequest req = new ReorderRequest();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        req.setIds(List.of(id1, id2));

        assertThat(req.getIds()).containsExactly(id1, id2);
    }
}
