package project.lms_rikkei_edu.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseOwnershipGuardTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private CourseOwnershipGuard guard;

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        guard = new CourseOwnershipGuard(courseRepository, currentUserProvider);
    }

    private UserPrincipal principal(UUID id, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRole(role);
        return new UserPrincipal(user);
    }

    @Test
    void throws_whenNoAuthenticatedUser() {
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireOwnership(courseId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chưa xác thực");

        verifyNoInteractions(courseRepository);
    }

    @Test
    void allows_whenAdmin_skipsOwnershipCheck() {
        when(currentUserProvider.getCurrentUser())
                .thenReturn(Optional.of(principal(UUID.randomUUID(), UserRole.ADMIN)));

        assertThatCode(() -> guard.requireOwnership(courseId)).doesNotThrowAnyException();

        verifyNoInteractions(courseRepository);
    }

    @Test
    void throws_whenInstructorDoesNotOwnCourse() {
        when(currentUserProvider.getCurrentUser())
                .thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireOwnership(courseId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("không có quyền");
    }

    @Test
    void allows_whenInstructorOwnsCourse() {
        when(currentUserProvider.getCurrentUser())
                .thenReturn(Optional.of(principal(instructorId, UserRole.INSTRUCTOR)));
        when(courseRepository.existsByIdAndInstructorId(courseId, instructorId)).thenReturn(true);

        assertThatCode(() -> guard.requireOwnership(courseId)).doesNotThrowAnyException();

        verify(courseRepository).existsByIdAndInstructorId(courseId, instructorId);
    }
}
