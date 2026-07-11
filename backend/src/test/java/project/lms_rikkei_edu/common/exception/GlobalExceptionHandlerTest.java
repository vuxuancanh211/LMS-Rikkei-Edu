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
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import project.lms_rikkei_edu.modules.ai.exception.AiSourceNotFoundException;
import project.lms_rikkei_edu.modules.ai.exception.ConversationNotFoundException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateAccessDeniedException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateAlreadyIssuedException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateNotFoundException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificatePdfException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateStateException;
import project.lms_rikkei_edu.modules.chat.exception.ChatAccessDeniedException;
import project.lms_rikkei_edu.modules.chat.exception.ChatMessageNotFoundException;
import project.lms_rikkei_edu.modules.chat.exception.ChatRoomNotFoundException;
import project.lms_rikkei_edu.modules.course.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

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

        @Test
        void noHandlerFound_returns404() throws Exception {
            mvc.perform(get("/fake/no-handler"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Endpoint not found"));
        }

        @Test
        void noResourceFound_returns404() throws Exception {
            mvc.perform(get("/fake/no-resource"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Resource not found"));
        }
    }

    @Nested
    class AiExceptions {

        @Test
        void aiSourceNotFound_returns404() throws Exception {
            mvc.perform(get("/fake/ai-source-not-found"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void conversationNotFound_returns404() throws Exception {
            mvc.perform(get("/fake/conversation-not-found"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class CertificateExceptions {

        @Test
        void certificateNotFound_returns404() throws Exception {
            mvc.perform(get("/fake/certificate-not-found"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void certificateAlreadyIssued_returns409() throws Exception {
            mvc.perform(get("/fake/certificate-already-issued"))
                    .andExpect(status().isConflict());
        }

        @Test
        void certificateState_returns409() throws Exception {
            mvc.perform(get("/fake/certificate-state"))
                    .andExpect(status().isConflict());
        }

        @Test
        void certificateAccessDenied_returns403() throws Exception {
            mvc.perform(get("/fake/certificate-access-denied"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void certificatePdf_returns500() throws Exception {
            mvc.perform(get("/fake/certificate-pdf"))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    class AsyncExceptions {

        @Test
        void asyncRequestTimeout_isSwallowedSilently() throws Exception {
            mvc.perform(get("/fake/async-timeout"))
                    .andExpect(status().isOk());
        }

        @Test
        void asyncRequestNotUsable_isSwallowedSilently() throws Exception {
            mvc.perform(get("/fake/async-not-usable"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class ChatExceptions {

        @Test
        void chatRoomNotFound_returns404() throws Exception {
            mvc.perform(get("/fake/chat-room-not-found"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void chatMessageNotFound_returns404() throws Exception {
            mvc.perform(get("/fake/chat-message-not-found"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void chatAccessDenied_returns403() throws Exception {
            mvc.perform(get("/fake/chat-access-denied"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class ValidationAndRequestExceptions {

        @Test
        void methodArgumentNotValid_returns400WithFieldErrors() throws Exception {
            mvc.perform(post("/fake/validated")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Dữ liệu nhập không hợp lệ"))
                    .andExpect(jsonPath("$.validationErrors.name").exists());
        }

        @Test
        void missingRequestParameter_returns400() throws Exception {
            mvc.perform(get("/fake/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("id")));
        }

        @Test
        void malformedRequestBody_returns400() throws Exception {
            mvc.perform(post("/fake/validated")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not valid json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Request body is invalid or malformed"));
        }
    }

    @Nested
    class UnhandledExceptionSpecialCases {

        @Test
        void httpMessageNotWritable_returns204() throws Exception {
            mvc.perform(get("/fake/message-not-writable"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void ioException_returns204() throws Exception {
            mvc.perform(get("/fake/io-exception"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void unhandledWithSseAcceptHeader_returns204() throws Exception {
            mvc.perform(get("/fake/unhandled").accept("text/event-stream"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void unhandledOnNotificationsConnectUri_returns204() throws Exception {
            mvc.perform(get("/fake/notifications/connect"))
                    .andExpect(status().isNoContent());
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

        @GetMapping("/certificate-not-found")
        void certificateNotFound() { throw new CertificateNotFoundException("Certificate not found"); }

        @GetMapping("/certificate-already-issued")
        void certificateAlreadyIssued() { throw new CertificateAlreadyIssuedException("Already issued"); }

        @GetMapping("/certificate-state")
        void certificateState() { throw new CertificateStateException("Invalid certificate state"); }

        @GetMapping("/certificate-access-denied")
        void certificateAccessDenied() { throw new CertificateAccessDeniedException("Access denied"); }

        @GetMapping("/certificate-pdf")
        void certificatePdf() { throw new CertificatePdfException("PDF generation failed"); }

        @PostMapping(value = "/require-json", consumes = MediaType.APPLICATION_JSON_VALUE)
        void requireJson(@RequestBody String body) {}

        @GetMapping("/constraint-violation")
        void constraintViolation() {
            ConstraintViolation<?> violation = mockViolation("field1", "must not be null");
            throw new ConstraintViolationException(Set.of(violation));
        }

        @GetMapping("/no-handler")
        void noHandler() throws NoHandlerFoundException {
            throw new NoHandlerFoundException("GET", "/unknown", org.springframework.http.HttpHeaders.EMPTY);
        }

        @GetMapping("/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/static/missing.js", "Resource not found");
        }

        @GetMapping("/ai-source-not-found")
        void aiSourceNotFound() { throw new AiSourceNotFoundException(UUID.randomUUID()); }

        @GetMapping("/conversation-not-found")
        void conversationNotFound() { throw new ConversationNotFoundException(UUID.randomUUID()); }

        @GetMapping("/async-timeout")
        void asyncTimeout() throws AsyncRequestTimeoutException {
            throw new AsyncRequestTimeoutException();
        }

        @GetMapping("/async-not-usable")
        void asyncNotUsable() throws org.springframework.web.context.request.async.AsyncRequestNotUsableException {
            throw new org.springframework.web.context.request.async.AsyncRequestNotUsableException(
                    "client disconnected", new java.io.IOException("broken pipe"));
        }

        @GetMapping("/chat-room-not-found")
        void chatRoomNotFound() { throw new ChatRoomNotFoundException(UUID.randomUUID()); }

        @GetMapping("/chat-message-not-found")
        void chatMessageNotFound() { throw new ChatMessageNotFoundException(UUID.randomUUID()); }

        @GetMapping("/chat-access-denied")
        void chatAccessDenied() { throw new ChatAccessDeniedException("Không có quyền truy cập"); }

        @PostMapping(value = "/validated", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validated(@Valid @RequestBody ValidatedRequest body) {}

        @GetMapping("/required-param")
        void requiredParam(@RequestParam UUID id) {}

        @GetMapping("/message-not-writable")
        void messageNotWritable() throws org.springframework.http.converter.HttpMessageNotWritableException {
            throw new org.springframework.http.converter.HttpMessageNotWritableException("cannot write");
        }

        @GetMapping("/io-exception")
        void ioException() throws java.io.IOException {
            throw new java.io.IOException("broken pipe");
        }

        @GetMapping("/notifications/connect")
        void notificationsConnect() throws Exception {
            throw new Exception("sse stream error");
        }

        static class ValidatedRequest {
            @NotBlank
            private String name;
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
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
