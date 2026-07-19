package project.lms_rikkei_edu.modules.course.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResourceTypeTest {

    @Test
    void enumValues() {
        assertThat(ResourceType.values()).containsExactly(
                ResourceType.PDF, ResourceType.DOC, ResourceType.SLIDE,
                ResourceType.IMAGE, ResourceType.VIDEO, ResourceType.OTHER
        );
    }
}
