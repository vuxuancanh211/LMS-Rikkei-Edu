package project.lms_rikkei_edu.modules.course.exception;

import java.util.UUID;

public class CourseNotFoundException extends RuntimeException {
    public CourseNotFoundException(UUID courseId) {
        super("Course not found: " + courseId);
    }

    public CourseNotFoundException(String slug) {
        super("Course not found: " + slug);
    }
}
