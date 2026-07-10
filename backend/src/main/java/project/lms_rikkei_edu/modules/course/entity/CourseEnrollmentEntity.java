package project.lms_rikkei_edu.modules.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "course_enrollments")
public class CourseEnrollmentEntity {

    @Id
    private UUID id;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "student_id")
    private UUID studentId;
}
