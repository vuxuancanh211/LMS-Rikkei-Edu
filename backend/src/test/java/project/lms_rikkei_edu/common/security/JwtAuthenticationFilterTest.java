package project.lms_rikkei_edu.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private RedisService redisService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withoutAuthHeader_proceeds() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_withNonBearerHeader_proceeds() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic abc123xyz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_withBlacklistedToken_clearsContextAndProceeds() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer token-abc");
        when(jwtService.extractJti("token-abc")).thenReturn("jti-123");
        when(redisService.isAccessTokenBlacklisted("jti-123")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void doFilterInternal_withValidToken_setsAuthentication() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.extractJti("valid-token")).thenReturn("jti-valid");
        when(redisService.isAccessTokenBlacklisted("jti-valid")).thenReturn(false);
        when(jwtService.extractUsername("valid-token")).thenReturn("test@example.com");

        UserDetails userDetails = new User("test@example.com", "password", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid-token", userDetails)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("test@example.com");
    }

    @Test
    void doFilterInternal_whenNullJtiAndInvalidToken_proceedsWithoutAuth() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtService.extractJti("invalid-token")).thenReturn(null);
        when(jwtService.extractUsername("invalid-token")).thenReturn("test@example.com");

        UserDetails userDetails = new User("test@example.com", "password", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("invalid-token", userDetails)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_whenEmailIsNull_proceedsWithoutAuth() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer token-no-email");
        when(jwtService.extractJti("token-no-email")).thenReturn("jti-ok");
        when(redisService.isAccessTokenBlacklisted("jti-ok")).thenReturn(false);
        when(jwtService.extractUsername("token-no-email")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void doFilterInternal_whenAlreadyAuthenticated_doesNotLoadUser() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer token-already-auth");
        when(jwtService.extractJti("token-already-auth")).thenReturn("jti-ok");
        when(redisService.isAccessTokenBlacklisted("jti-ok")).thenReturn(false);
        when(jwtService.extractUsername("token-already-auth")).thenReturn("test@example.com");

        UsernamePasswordAuthenticationToken existingAuth = new UsernamePasswordAuthenticationToken("existing", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void doFilterInternal_whenRuntimeExceptionThrown_clearsContextAndProceeds() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtService.extractJti("bad-token")).thenThrow(new RuntimeException("Malformed JWT"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
