package project.lms_rikkei_edu.modules.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.lms_rikkei_edu.modules.course.entity.CourseCategory;

import java.util.UUID;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID> {
}
