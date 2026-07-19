package project.lms_rikkei_edu.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;

import java.util.UUID;

/**
 * Helper lấy thông tin instructor hiện tại (Đã tích hợp JWT / SecurityContextHolder).
 */
@Deprecated(since = "1.0", forRemoval = true)
@Component
public class InstructorContext {

    private static final String USER_ID_HEADER = "X-User-Id";
    private CurrentUserProvider currentUserProvider;

    public InstructorContext() {
    }

    @Autowired
    public InstructorContext(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    public UUID getCurrentInstructorId(HttpServletRequest request) {
        if (currentUserProvider != null) {
            UUID id = currentUserProvider.getCurrentUserId().orElse(null);
            if (id != null) {
                return id;
            }
        }
        String userId = request != null ? request.getHeader(USER_ID_HEADER) : null;
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Missing " + USER_ID_HEADER + " header and no JWT authentication found in SecurityContext");
        }
        return UUID.fromString(userId);
    }
}
