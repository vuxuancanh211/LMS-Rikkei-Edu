package project.lms_rikkei_edu.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @Mock SetOperations<String, Object> setOps;

    RedisService redisService;

    private final UUID userId = UUID.randomUUID();
    private final String tokenHash = "hashed-token";
    private final String jti = "jti-123";

    @BeforeEach
    void setUp() {
        redisService = new RedisService(redisTemplate);
        ReflectionTestUtils.setField(redisService, "refreshTokenTtl", 86400L);
        ReflectionTestUtils.setField(redisService, "passwordResetTokenTtl", 3600L);
        ReflectionTestUtils.setField(redisService, "rateLimitWindow", 60L);
        ReflectionTestUtils.setField(redisService, "rateLimitMax", 5L);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    // ── Generic ───────────────────────────────────────────────────────────────

    @Test
    void set_storesValueWithTtl() {
        redisService.set("key", "value", 100L);
        verify(valueOps).set("key", "value", Duration.ofSeconds(100L));
    }

    @Test
    void get_returnsValue_whenExists() {
        when(valueOps.get("key")).thenReturn("value");
        Optional<Object> result = redisService.get("key");
        assertThat(result).contains("value");
    }

    @Test
    void get_returnsEmpty_whenNotExists() {
        when(valueOps.get("key")).thenReturn(null);
        Optional<Object> result = redisService.get("key");
        assertThat(result).isEmpty();
    }

    @Test
    void delete_deletesKey() {
        redisService.delete("key");
        verify(redisTemplate).delete("key");
    }

    @Test
    void hasKey_returnsTrue_whenExists() {
        when(redisTemplate.hasKey("key")).thenReturn(true);
        assertThat(redisService.hasKey("key")).isTrue();
    }

    @Test
    void hasKey_returnsFalse_whenNotExists() {
        when(redisTemplate.hasKey("key")).thenReturn(false);
        assertThat(redisService.hasKey("key")).isFalse();
    }

    // ── JWT Blacklist ─────────────────────────────────────────────────────────

    @Test
    void blacklistAccessToken_setsKeyWithRemainingTtl() {
        Date future = new Date(System.currentTimeMillis() + 5000);
        redisService.blacklistAccessToken(jti, future);
        verify(valueOps).set(eq(RedisKeyConstants.ACCESS_TOKEN_BLACKLIST + jti), eq("blacklisted"), any(Duration.class));
    }

    @Test
    void blacklistAccessToken_skips_whenExpired() {
        Date past = new Date(System.currentTimeMillis() - 1000);
        redisService.blacklistAccessToken(jti, past);
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void isAccessTokenBlacklisted_checksKey() {
        when(redisTemplate.hasKey(RedisKeyConstants.ACCESS_TOKEN_BLACKLIST + jti)).thenReturn(true);
        assertThat(redisService.isAccessTokenBlacklisted(jti)).isTrue();
    }

    @Test
    void isAccessTokenBlacklisted_returnsFalse_whenRedisCheckFails() {
        when(redisTemplate.hasKey(RedisKeyConstants.ACCESS_TOKEN_BLACKLIST + jti))
                .thenThrow(new RuntimeException("Redis unavailable"));

        assertThat(redisService.isAccessTokenBlacklisted(jti)).isFalse();
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Test
    void saveRefreshToken_setsTokenAndUserTokens() {
        redisService.saveRefreshToken(userId, tokenHash);
        verify(valueOps).set(eq(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash), eq(tokenHash), any(Duration.class));
        verify(valueOps).set(eq(RedisKeyConstants.REFRESH_TOKEN + userId), eq(tokenHash), any(Duration.class));
        verify(setOps).add(RedisKeyConstants.USER_TOKENS + userId, tokenHash);
        verify(redisTemplate).expire(eq(RedisKeyConstants.USER_TOKENS + userId), any(Duration.class));
    }

    @Test
    void getRefreshToken_returnsFromLegacy_whenPresent() {
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn(tokenHash);
        Optional<String> result = redisService.getRefreshToken(userId);
        assertThat(result).contains(tokenHash);
    }

    @Test
    void getRefreshToken_returnsFromSet_whenLegacyAbsent() {
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn(null);
        when(setOps.members(RedisKeyConstants.USER_TOKENS + userId)).thenReturn(Set.of(tokenHash));
        Optional<String> result = redisService.getRefreshToken(userId);
        assertThat(result).contains(tokenHash);
    }

    @Test
    void getRefreshToken_returnsEmpty_whenNoneExists() {
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn(null);
        when(setOps.members(RedisKeyConstants.USER_TOKENS + userId)).thenReturn(null);
        Optional<String> result = redisService.getRefreshToken(userId);
        assertThat(result).isEmpty();
    }

    @Test
    void getRefreshToken_returnsEmpty_whenSetIsEmpty() {
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn(null);
        when(setOps.members(RedisKeyConstants.USER_TOKENS + userId)).thenReturn(Set.of());
        Optional<String> result = redisService.getRefreshToken(userId);
        assertThat(result).isEmpty();
    }

    @Test
    void isRefreshTokenValid_returnsTrue_whenExactKeyExists() {
        when(redisTemplate.hasKey(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash)).thenReturn(true);
        assertThat(redisService.isRefreshTokenValid(userId, tokenHash)).isTrue();
    }

    @Test
    void isRefreshTokenValid_checksLegacy_whenExactKeyAbsent() {
        when(redisTemplate.hasKey(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash)).thenReturn(false);
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn(tokenHash);
        assertThat(redisService.isRefreshTokenValid(userId, tokenHash)).isTrue();
    }

    @Test
    void isRefreshTokenValid_checksSetMembership() {
        when(redisTemplate.hasKey(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash)).thenReturn(false);
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn(null);
        assertThat(redisService.isRefreshTokenValid(userId, tokenHash)).isFalse();
    }

    @Test
    void rotateRefreshTokenWithGracePeriod_extendsExactKey() {
        when(redisTemplate.hasKey(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash)).thenReturn(true);
        redisService.rotateRefreshTokenWithGracePeriod(userId, tokenHash, 30L);
        verify(redisTemplate).expire(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash, Duration.ofSeconds(30L));
    }

    @Test
    void rotateRefreshTokenWithGracePeriod_extendsLegacyKey() {
        when(redisTemplate.hasKey(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash)).thenReturn(false);
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn(tokenHash);
        redisService.rotateRefreshTokenWithGracePeriod(userId, tokenHash, 30L);
        verify(redisTemplate).expire(RedisKeyConstants.REFRESH_TOKEN + userId, Duration.ofSeconds(30L));
    }

    @Test
    void deleteRefreshToken_withHash_removesTokenAndSetMember() {
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn(tokenHash);
        redisService.deleteRefreshToken(userId, tokenHash);
        verify(redisTemplate).delete(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash);
        verify(setOps).remove(RedisKeyConstants.USER_TOKENS + userId, tokenHash);
        verify(redisTemplate).delete(RedisKeyConstants.REFRESH_TOKEN + userId);
    }

    @Test
    void deleteRefreshToken_withHash_doesNotDeleteLegacy_whenDifferent() {
        when(valueOps.get(RedisKeyConstants.REFRESH_TOKEN + userId)).thenReturn("other-hash");
        redisService.deleteRefreshToken(userId, tokenHash);
        verify(redisTemplate).delete(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash);
        verify(setOps).remove(RedisKeyConstants.USER_TOKENS + userId, tokenHash);
        verify(redisTemplate, never()).delete(RedisKeyConstants.REFRESH_TOKEN + userId);
    }

    @Test
    void deleteRefreshToken_byUserId_deletesAll() {
        when(setOps.members(RedisKeyConstants.USER_TOKENS + userId)).thenReturn(Set.of(tokenHash, "other-hash"));
        redisService.deleteRefreshToken(userId);
        verify(redisTemplate).delete(RedisKeyConstants.REFRESH_TOKEN + userId);
        verify(redisTemplate).delete(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash);
        verify(redisTemplate).delete(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + "other-hash");
        verify(redisTemplate).delete(RedisKeyConstants.USER_TOKENS + userId);
    }

    @Test
    void deleteAllRefreshTokens_handlesNullTokens() {
        when(setOps.members(RedisKeyConstants.USER_TOKENS + userId)).thenReturn(null);
        redisService.deleteAllRefreshTokens(userId);
        verify(redisTemplate).delete(RedisKeyConstants.REFRESH_TOKEN + userId);
        verify(redisTemplate).delete(RedisKeyConstants.USER_TOKENS + userId);
    }

    // ── Password Reset Token ─────────────────────────────────────────────────

    @Test
    void savePasswordResetToken_setsWithTtl() {
        redisService.savePasswordResetToken(tokenHash, userId);
        verify(valueOps).set(eq(RedisKeyConstants.PASSWORD_RESET_TOKEN + tokenHash), eq(userId.toString()), any(Duration.class));
    }

    @Test
    void getPasswordResetUserId_returnsUserId_whenExists() {
        when(valueOps.get(RedisKeyConstants.PASSWORD_RESET_TOKEN + tokenHash)).thenReturn(userId.toString());
        Optional<UUID> result = redisService.getPasswordResetUserId(tokenHash);
        assertThat(result).contains(userId);
    }

    @Test
    void getPasswordResetUserId_returnsEmpty_whenNotExists() {
        when(valueOps.get(RedisKeyConstants.PASSWORD_RESET_TOKEN + tokenHash)).thenReturn(null);
        Optional<UUID> result = redisService.getPasswordResetUserId(tokenHash);
        assertThat(result).isEmpty();
    }

    @Test
    void deletePasswordResetToken_deletesKey() {
        redisService.deletePasswordResetToken(tokenHash);
        verify(redisTemplate).delete(RedisKeyConstants.PASSWORD_RESET_TOKEN + tokenHash);
    }

    // ── Rate Limit ────────────────────────────────────────────────────────────

    @Test
    void isRateLimited_returnsFalse_whenUnderLimit() {
        when(valueOps.increment(RedisKeyConstants.RATE_LIMIT + "test-key")).thenReturn(1L);
        assertThat(redisService.isRateLimited("test-key")).isFalse();
        verify(redisTemplate).expire(eq(RedisKeyConstants.RATE_LIMIT + "test-key"), any(Duration.class));
    }

    @Test
    void isRateLimited_returnsTrue_whenOverLimit() {
        when(valueOps.increment(RedisKeyConstants.RATE_LIMIT + "test-key")).thenReturn(10L);
        assertThat(redisService.isRateLimited("test-key")).isTrue();
    }

    @Test
    void getRateLimitRemaining_returnsMax_whenNoKey() {
        when(valueOps.get(RedisKeyConstants.RATE_LIMIT + "test-key")).thenReturn(null);
        assertThat(redisService.getRateLimitRemaining("test-key")).isEqualTo(5L);
    }

    @Test
    void getRateLimitRemaining_returnsRemaining() {
        when(valueOps.get(RedisKeyConstants.RATE_LIMIT + "test-key")).thenReturn("2");
        assertThat(redisService.getRateLimitRemaining("test-key")).isEqualTo(3L);
    }

    @Test
    void getRateLimitRemaining_returnsZero_whenExceeded() {
        when(valueOps.get(RedisKeyConstants.RATE_LIMIT + "test-key")).thenReturn("10");
        assertThat(redisService.getRateLimitRemaining("test-key")).isZero();
    }

    // ── SSE Connected Users ───────────────────────────────────────────────────

    @Test
    void addSseUser_addsToSet() {
        redisService.addSseUser("user-1");
        verify(setOps).add(RedisKeyConstants.SSE_CONNECTED_USERS, "user-1");
    }

    @Test
    void removeSseUser_removesFromSet() {
        redisService.removeSseUser("user-1");
        verify(setOps).remove(RedisKeyConstants.SSE_CONNECTED_USERS, "user-1");
    }

    @Test
    void isSseUserConnected_checksMembership() {
        when(setOps.isMember(RedisKeyConstants.SSE_CONNECTED_USERS, "user-1")).thenReturn(true);
        assertThat(redisService.isSseUserConnected("user-1")).isTrue();
    }

    @Test
    void getAllSseUsers_returnsAllMembers() {
        when(setOps.members(RedisKeyConstants.SSE_CONNECTED_USERS)).thenReturn(Set.of("user-1", "user-2"));
        Set<Object> result = redisService.getAllSseUsers();
        assertThat(result).containsExactlyInAnyOrder("user-1", "user-2");
    }
}
