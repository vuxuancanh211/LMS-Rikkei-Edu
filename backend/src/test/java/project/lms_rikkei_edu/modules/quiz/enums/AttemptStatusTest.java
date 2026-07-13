package project.lms_rikkei_edu.modules.quiz.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AttemptStatusTest {

    @Test
    void enumValues() {
        assertThat(AttemptStatus.values()).containsExactly(
                AttemptStatus.IN_PROGRESS, AttemptStatus.SUBMITTED,
                AttemptStatus.GRADED, AttemptStatus.EXPIRED
        );
    }
}
