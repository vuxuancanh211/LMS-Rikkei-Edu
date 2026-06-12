package project.lms_rikkei_edu.modules.course.exception;

import java.util.UUID;

public class ChapterNotFoundException extends RuntimeException {
    public ChapterNotFoundException(UUID chapterId) {
        super("Chapter not found: " + chapterId);
    }
}
