package project.lms_rikkei_edu.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.common.auth.InstructorContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstructorContextTest {

    private final InstructorContext context = new InstructorContext();

    @Test
    void returnsUuid_whenHeaderIsValid() {
        UUID expected = UUID.randomUUID();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Id")).thenReturn(expected.toString());

        UUID result = context.getCurrentInstructorId(req);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void throwsIllegalState_whenHeaderIsMissing() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Id")).thenReturn(null);

        assertThatThrownBy(() -> context.getCurrentInstructorId(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X-User-Id");
    }

    @Test
    void throwsIllegalState_whenHeaderIsBlank() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Id")).thenReturn("   ");

        assertThatThrownBy(() -> context.getCurrentInstructorId(req))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsIllegalArgument_whenHeaderIsInvalidUuid() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Id")).thenReturn("not-a-uuid");

        assertThatThrownBy(() -> context.getCurrentInstructorId(req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
