package project.lms_rikkei_edu.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "my-secret-key-that-is-at-least-32-characters-long-!!";
    private static final long EXPIRATION = 3600000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION);
    }

    @Test
    void generateAndExtractUsername() {
        UserEntity user = createUser("test@example.com", UserRole.STUDENT);
        String token = jwtService.generateAccessToken(user);

        String extracted = jwtService.extractUsername(token);

        assertThat(extracted).isEqualTo("test@example.com");
    }

    @Test
    void generateAndExtractUserId() {
        UserEntity user = createUser("test@example.com", UserRole.STUDENT);
        String token = jwtService.generateAccessToken(user);

        UUID extracted = jwtService.extractUserId(token);

        assertThat(extracted).isEqualTo(user.getId());
    }

    @Test
    void generateAndExtractJti() {
        UserEntity user = createUser("test@example.com", UserRole.STUDENT);
        String token = jwtService.generateAccessToken(user);

        String jti = jwtService.extractJti(token);

        assertThat(jti).isNotNull();
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        UserEntity user = createUser("test@example.com", UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(user);
        UserPrincipal principal = new UserPrincipal(user);

        boolean valid = jwtService.isTokenValid(token, principal);

        assertThat(valid).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseWhenUsernameMismatch() {
        UserEntity user = createUser("test@example.com", UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(user);
        UserEntity otherUser = createUser("other@example.com", UserRole.STUDENT);
        otherUser.setStatus(UserStatus.ACTIVE);
        UserPrincipal otherPrincipal = new UserPrincipal(otherUser);

        boolean valid = jwtService.isTokenValid(token, otherPrincipal);

        assertThat(valid).isFalse();
    }

    @Test
    void isTokenExpired_returnsFalseForFreshToken() {
        UserEntity user = createUser("test@example.com", UserRole.STUDENT);
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractExpiration(token)).isAfter(new Date());
    }

    @Test
    void resolveToken_returnsTokenFromHeader() {
        UserEntity user = createUser("test@example.com", UserRole.STUDENT);
        String token = jwtService.generateAccessToken(user);
        String header = "Bearer " + token;

        String resolved = jwtService.resolveToken(header);

        assertThat(resolved).isEqualTo(token);
    }

    @Test
    void resolveToken_returnsNullForInvalidPrefix() {
        assertThat(jwtService.resolveToken("Basic abc")).isNull();
    }

    @Test
    void resolveToken_returnsNullForNullHeader() {
        assertThat(jwtService.resolveToken(null)).isNull();
    }

    @Test
    void extractUserId_returnsNullWhenClaimMissing() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("test@example.com")
                .signWith(key)
                .compact();

        UUID userId = jwtService.extractUserId(token);

        assertThat(userId).isNull();
    }

    @Test
    void getAccessTokenExpirationSeconds_returnsCorrectValue() {
        assertThat(jwtService.getAccessTokenExpirationSeconds()).isEqualTo(3600);
    }

    private UserEntity createUser(String email, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
