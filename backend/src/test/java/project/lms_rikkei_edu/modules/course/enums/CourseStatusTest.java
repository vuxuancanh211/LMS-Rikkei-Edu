package project.lms_rikkei_edu.modules.course.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CourseStatusTest {

    @Test
    void enumValues() {
        assertThat(CourseStatus.values()).containsExactly(
                CourseStatus.DRAFT, CourseStatus.PENDING, CourseStatus.APPROVED,
                CourseStatus.REJECTED, CourseStatus.PUBLISHED,
                CourseStatus.PENDING_UPDATE, CourseStatus.ARCHIVED
        );
    }
}
