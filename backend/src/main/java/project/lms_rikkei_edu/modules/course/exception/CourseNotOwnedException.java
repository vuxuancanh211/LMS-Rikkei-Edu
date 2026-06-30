package project.lms_rikkei_edu.modules.course.exception;

public class CourseNotOwnedException extends RuntimeException {
    public CourseNotOwnedException() {
        super("You do not own this course");
    }
}
