package project.lms_rikkei_edu.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Placeholder để lấy thông tin instructor hiện tại.
 *
 * TODO: Khi JWT được tích hợp, thay thế toàn bộ nội dung class này bằng
 *       SecurityContextHolder.getContext().getAuthentication() để lấy UUID từ JWT claims.
 *       Controller không cần thay đổi vì chỉ gọi getCurrentInstructorId().
 */
@Component
public class InstructorContext {

    private static final String USER_ID_HEADER = "X-User-Id";

    public UUID getCurrentInstructorId(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Missing " + USER_ID_HEADER + " header");
        }
        return UUID.fromString(userId);
    }
}
