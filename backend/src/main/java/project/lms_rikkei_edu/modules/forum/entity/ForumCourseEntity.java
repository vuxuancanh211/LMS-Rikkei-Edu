package project.lms_rikkei_edu.modules.forum.entity;

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
@Table(name = "courses")
public class ForumCourseEntity {

    @Id
    private UUID id;

    @Column(name = "instructor_id")
    private UUID instructorId;

    @Column(length = 200)
    private String title;

    @Column(length = 30)
    private String status;
}
