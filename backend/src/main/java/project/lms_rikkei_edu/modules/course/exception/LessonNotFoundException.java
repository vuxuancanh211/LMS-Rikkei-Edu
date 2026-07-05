package project.lms_rikkei_edu.modules.course.exception;

import java.util.UUID;

public class LessonNotFoundException extends RuntimeException {
    public LessonNotFoundException(UUID lessonId) {
        super("Lesson not found: " + lessonId);
    }
}
