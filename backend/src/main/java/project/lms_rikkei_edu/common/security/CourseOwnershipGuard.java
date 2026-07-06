package project.lms_rikkei_edu.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.user.enums.UserRole;

import java.util.UUID;

/**
 * Kiểm tra giảng viên chỉ được thao tác trên khóa học của mình.
 * ADMIN bỏ qua kiểm tra này.
 */
@Component
@RequiredArgsConstructor
public class CourseOwnershipGuard {

    private final CourseRepository courseRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Ném AccessDeniedException (HTTP 403) nếu người dùng hiện tại là INSTRUCTOR
     * nhưng không sở hữu courseId.
     */
    public void requireOwnership(UUID courseId) {
        UserPrincipal principal = currentUserProvider.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("Chưa xác thực"));

        if (principal.getRole() == UserRole.ADMIN) {
            return;
        }

        UUID instructorId = principal.getId();
        if (!courseRepository.existsByIdAndInstructorId(courseId, instructorId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập khóa học này");
        }
    }
}
