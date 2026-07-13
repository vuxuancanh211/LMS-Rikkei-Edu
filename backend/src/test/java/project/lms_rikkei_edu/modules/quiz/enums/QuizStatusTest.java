package project.lms_rikkei_edu.modules.quiz.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QuizStatusTest {

    @Test
    void enumValues() {
        assertThat(QuizStatus.values()).containsExactly(
                QuizStatus.DRAFT, QuizStatus.PUBLISHED, QuizStatus.ARCHIVED
        );
    }
}
