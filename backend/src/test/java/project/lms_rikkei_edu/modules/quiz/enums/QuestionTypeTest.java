package project.lms_rikkei_edu.modules.quiz.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QuestionTypeTest {

    @Test
    void enumValues() {
        assertThat(QuestionType.values()).containsExactly(
                QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE, QuestionType.TRUE_FALSE
        );
    }
}
