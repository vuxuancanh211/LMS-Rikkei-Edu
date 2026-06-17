package project.lms_rikkei_edu.common.exception;

import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import project.lms_rikkei_edu.modules.ai.exception.*;
import project.lms_rikkei_edu.modules.course.exception.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Auth / Business exceptions ────────────────────────────────────────────

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    // ── Course exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(CourseNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCourseNotFound(
            CourseNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler({ChapterNotFoundException.class, LessonNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(
            RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(CourseNotOwnedException.class)
    public ResponseEntity<ErrorResponse> handleNotOwned(
            CourseNotOwnedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(CourseStateException.class)
    public ResponseEntity<ErrorResponse> handleCourseState(
            CourseStateException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI(), null);
    }

    // ── AI exceptions ─────────────────────────────────────────────────────────

    @ExceptionHandler(UserContextException.class)
    public ResponseEntity<ErrorResponse> handleUserContext(
            UserContextException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(AiSourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAiSourceNotFound(
            AiSourceNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConversationNotFound(
            ConversationNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(IngestionException.class)
    public ResponseEntity<ErrorResponse> handleIngestion(
            IngestionException ex, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI(), null);
    }

    // ── Generic exceptions ────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupported(
            UnsupportedOperationException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                validationErrors.put(e.getField(), e.getDefaultMessage())
        );
        return error(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), validationErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.",
                request.getRequestURI(), null);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, String path,
                                                 Map<String, String> validationErrors) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .validationErrors(validationErrors)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
