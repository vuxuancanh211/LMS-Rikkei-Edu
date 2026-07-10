package project.lms_rikkei_edu.modules.course.entity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CourseEnrollmentEntityTest {

    @Test
    void testCourseEnrollmentEntityGettersAndSetters() {
        CourseEnrollmentEntity entity = new CourseEnrollmentEntity();
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enrolledBy = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setCourseId(courseId);
        entity.setStudentId(studentId);
        entity.setEnrolledBy(enrolledBy);
        entity.setEnrolledAt(now);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCourseId()).isEqualTo(courseId);
        assertThat(entity.getStudentId()).isEqualTo(studentId);
        assertThat(entity.getEnrolledBy()).isEqualTo(enrolledBy);
        assertThat(entity.getEnrolledAt()).isEqualTo(now);
    }
}
