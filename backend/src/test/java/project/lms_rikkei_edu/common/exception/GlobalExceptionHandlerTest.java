package project.lms_rikkei_edu.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import project.lms_rikkei_edu.modules.course.exception.*;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class CourseExceptions {

        @Test
        void courseNotFound_returns404() throws Exception {
            mvc.perform(get("/fake/course-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void chapterNotFound_returns404() throws Exception {
            mvc.perform(get("/fake/chapter-not-found"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void lessonNotFound_returns404() throws Exception {
            mvc.perform(get("/fake/lesson-not-found"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void courseNotOwned_returns403() throws Exception {
            mvc.perform(get("/fake/not-owned"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("You do not own this course"));
        }

        @Test
        void courseState_returns409() throws Exception {
            mvc.perform(get("/fake/course-state"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class AuthExceptions {

        @Test
        void authenticationException_returns401() throws Exception {
            mvc.perform(get("/fake/unauthenticated"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void accessDenied_returns403() throws Exception {
            mvc.perform(get("/fake/access-denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Access denied"));
        }
    }

    @Nested
    class BusinessAndGenericExceptions {

        @Test
        void businessException_returnsConfiguredStatus() throws Exception {
            mvc.perform(get("/fake/business"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("business error"));
        }

        @Test
        void illegalArgument_returns400() throws Exception {
            mvc.perform(get("/fake/illegal-argument"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("bad arg"));
        }

        @Test
        void illegalState_returns409() throws Exception {
            mvc.perform(get("/fake/illegal-state"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("bad state"));
        }

        @Test
        void unhandledException_returns500() throws Exception {
            mvc.perform(get("/fake/unhandled"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Internal server error"));
        }
    }

    @Nested
    class HttpExceptions {

        @Test
        void wrongMethod_returns405() throws Exception {
            mvc.perform(delete("/fake/course-not-found"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        void wrongContentType_returns415() throws Exception {
            mvc.perform(post("/fake/require-json")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("text"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void constraintViolation_returns400() throws Exception {
            mvc.perform(get("/fake/constraint-violation"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Dữ liệu nhập không hợp lệ"));
        }
    }

    // ── Fake controller that throws each exception type ───────────────────────

    @RestController
    @RequestMapping("/fake")
    static class FakeController {

        @GetMapping("/course-not-found")
        void courseNotFound() { throw new CourseNotFoundException(UUID.randomUUID()); }

        @GetMapping("/chapter-not-found")
        void chapterNotFound() {
            throw new ChapterNotFoundException(UUID.randomUUID());
        }

        @GetMapping("/lesson-not-found")
        void lessonNotFound() {
            throw new LessonNotFoundException(UUID.randomUUID());
        }

        @GetMapping("/not-owned")
        void notOwned() { throw new CourseNotOwnedException(); }

        @GetMapping("/course-state")
        void courseState() { throw new CourseStateException("Invalid state"); }

        @GetMapping("/unauthenticated")
        void unauthenticated() { throw new BadCredentialsException("bad creds"); }

        @GetMapping("/access-denied")
        void accessDenied() { throw new AccessDeniedException("denied"); }

        @GetMapping("/business")
        void business() { throw new BusinessException("business error"); }

        @GetMapping("/illegal-argument")
        void illegalArg() { throw new IllegalArgumentException("bad arg"); }

        @GetMapping("/illegal-state")
        void illegalState() { throw new IllegalStateException("bad state"); }

        @GetMapping("/unhandled")
        void unhandled() throws Exception { throw new Exception("unhandled"); }

        @PostMapping(value = "/require-json", consumes = MediaType.APPLICATION_JSON_VALUE)
        void requireJson(@RequestBody String body) {}

        @GetMapping("/constraint-violation")
        void constraintViolation() {
            ConstraintViolation<?> violation = mockViolation("field1", "must not be null");
            throw new ConstraintViolationException(Set.of(violation));
        }

        private static ConstraintViolation<?> mockViolation(String field, String message) {
            return new ConstraintViolation<>() {
                @Override public String getMessage() { return message; }
                @Override public String getMessageTemplate() { return message; }
                @Override public Object getRootBean() { return null; }
                @Override public Class getRootBeanClass() { return Object.class; }
                @Override public Object getLeafBean() { return null; }
                @Override public Object[] getExecutableParameters() { return new Object[0]; }
                @Override public Object getExecutableReturnValue() { return null; }
                @Override public Path getPropertyPath() {
                    return new Path() {
                        @Override public java.util.Iterator<Node> iterator() { return java.util.Collections.emptyIterator(); }
                        @Override public String toString() { return field; }
                    };
                }
                @Override public Object getInvalidValue() { return null; }
                @Override public jakarta.validation.metadata.ConstraintDescriptor<?> getConstraintDescriptor() { return null; }
                @Override public <U> U unwrap(Class<U> type) { return null; }
            };
        }
    }
}
