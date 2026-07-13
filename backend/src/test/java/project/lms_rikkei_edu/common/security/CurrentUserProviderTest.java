package project.lms_rikkei_edu.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsCurrentUserWhenAuthenticated() {
        UserEntity user = createUser("test@example.com", UserRole.STUDENT);
        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<UserPrincipal> result = provider.getCurrentUser();

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(user.getId());
        assertThat(result.get().getUsername()).isEqualTo(user.getEmail());
    }

    @Test
    void returnsEmptyWhenNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(null);

        Optional<UserPrincipal> result = provider.getCurrentUser();

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenPrincipalIsNotUserPrincipal() {
        Authentication auth = new UsernamePasswordAuthenticationToken("anonymous", null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<UserPrincipal> result = provider.getCurrentUser();

        assertThat(result).isEmpty();
    }

    @Test
    void returnsUserIdWhenAuthenticated() {
        UserEntity user = createUser("test@example.com", UserRole.INSTRUCTOR);
        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<UUID> result = provider.getCurrentUserId();

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(user.getId());
    }

    @Test
    void returnsEmptyUserIdWhenNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(null);

        Optional<UUID> result = provider.getCurrentUserId();

        assertThat(result).isEmpty();
    }

    private UserEntity createUser(String email, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
