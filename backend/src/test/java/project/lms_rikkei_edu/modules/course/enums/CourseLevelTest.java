package project.lms_rikkei_edu.modules.course.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CourseLevelTest {

    @Test
    void enumValues() {
        assertThat(CourseLevel.values()).containsExactly(
                CourseLevel.BEGINNER, CourseLevel.INTERMEDIATE, CourseLevel.ADVANCED
        );
    }

    @Test
    void enumValueOf() {
        assertThat(CourseLevel.valueOf("BEGINNER")).isEqualTo(CourseLevel.BEGINNER);
        assertThat(CourseLevel.valueOf("INTERMEDIATE")).isEqualTo(CourseLevel.INTERMEDIATE);
        assertThat(CourseLevel.valueOf("ADVANCED")).isEqualTo(CourseLevel.ADVANCED);
    }
}
