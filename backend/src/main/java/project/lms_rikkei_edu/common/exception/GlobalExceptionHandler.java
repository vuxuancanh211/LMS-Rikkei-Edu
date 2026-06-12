package project.lms_rikkei_edu.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import project.lms_rikkei_edu.modules.ai.exception.*;
import project.lms_rikkei_edu.modules.course.exception.*;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Course exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(CourseNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCourseNotFound(CourseNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler({ChapterNotFoundException.class, LessonNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CourseNotOwnedException.class)
    public ResponseEntity<Map<String, Object>> handleNotOwned(CourseNotOwnedException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(CourseStateException.class)
    public ResponseEntity<Map<String, Object>> handleCourseState(CourseStateException ex) {
        return error(HttpStatus.CONFLICT, "INVALID_COURSE_STATE", ex.getMessage());
    }

    // ── AI exceptions ─────────────────────────────────────────────────────────

    @ExceptionHandler(UserContextException.class)
    public ResponseEntity<Map<String, Object>> handleUserContext(UserContextException ex) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(AiSourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAiSourceNotFound(AiSourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "AI_SOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleConversationNotFound(ConversationNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(IngestionException.class)
    public ResponseEntity<Map<String, Object>> handleIngestion(IngestionException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INGESTION_FAILED", ex.getMessage());
    }

    // ── Generic exceptions ────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupported(UnsupportedOperationException ex) {
        return error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_OPERATION", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}
