package project.lms_rikkei_edu.modules.course.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LessonTypeTest {

    @Test
    void enumValues() {
        assertThat(LessonType.values()).containsExactly(
                LessonType.VIDEO, LessonType.TEXT, LessonType.PDF, LessonType.DOC, LessonType.QUIZ
        );
    }
}
