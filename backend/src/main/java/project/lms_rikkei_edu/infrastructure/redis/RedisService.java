package project.lms_rikkei_edu.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.common.constants.RedisKeyConstants;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.redis.ttl.refresh-token}")
    private long refreshTokenTtl;

    @Value("${app.redis.ttl.password-reset-token}")
    private long passwordResetTokenTtl;

    @Value("${app.redis.ttl.rate-limit-window}")
    private long rateLimitWindow;

    @Value("${app.redis.ttl.rate-limit-max}")
    private long rateLimitMax;

    // ── Generic ───────────────────────────────────────────────────────────────

    public void set(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ── JWT Blacklist ─────────────────────────────────────────────────────────

    public void blacklistAccessToken(String jti, Date tokenExpiration) {
        long remainingTtl = (tokenExpiration.getTime() - System.currentTimeMillis()) / 1000;
        if (remainingTtl <= 0) return;

        String key = RedisKeyConstants.ACCESS_TOKEN_BLACKLIST + jti;
        redisTemplate.opsForValue().set(key, "blacklisted", Duration.ofSeconds(remainingTtl));
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        return hasKey(RedisKeyConstants.ACCESS_TOKEN_BLACKLIST + jti);
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    public void saveRefreshToken(UUID userId, String tokenHash) {
        String key = RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash;
        redisTemplate.opsForValue().set(key, tokenHash, Duration.ofSeconds(refreshTokenTtl));
        redisTemplate.opsForValue().set(RedisKeyConstants.REFRESH_TOKEN + userId, tokenHash, Duration.ofSeconds(refreshTokenTtl));
        redisTemplate.opsForSet().add(RedisKeyConstants.USER_TOKENS + userId, tokenHash);
        redisTemplate.expire(RedisKeyConstants.USER_TOKENS + userId, Duration.ofSeconds(refreshTokenTtl));
    }

    public Optional<String> getRefreshToken(UUID userId) {
        Optional<Object> legacy = get(RedisKeyConstants.REFRESH_TOKEN + userId);
        if (legacy.isPresent()) {
            return legacy.map(Object::toString);
        }
        Set<Object> tokens = redisTemplate.opsForSet().members(RedisKeyConstants.USER_TOKENS + userId);
        if (tokens != null && !tokens.isEmpty()) {
            return Optional.ofNullable(tokens.iterator().next()).map(Object::toString);
        }
        return Optional.empty();
    }

    public boolean isRefreshTokenValid(UUID userId, String tokenHash) {
        String key = RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return true;
        }
        Optional<String> legacy = getRefreshToken(userId);
        return legacy.isPresent() && tokenHash.equals(legacy.get());
    }

    public void rotateRefreshTokenWithGracePeriod(UUID userId, String tokenHash, long graceSeconds) {
        String key = RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.expire(key, Duration.ofSeconds(graceSeconds));
        }
        Optional<String> legacy = getRefreshToken(userId);
        if (legacy.isPresent() && tokenHash.equals(legacy.get())) {
            redisTemplate.expire(RedisKeyConstants.REFRESH_TOKEN + userId, Duration.ofSeconds(graceSeconds));
        }
    }

    public void deleteRefreshToken(UUID userId, String tokenHash) {
        String key = RedisKeyConstants.REFRESH_TOKEN + userId + ":" + tokenHash;
        delete(key);
        redisTemplate.opsForSet().remove(RedisKeyConstants.USER_TOKENS + userId, tokenHash);
        Optional<String> legacy = getRefreshToken(userId);
        if (legacy.isPresent() && tokenHash.equals(legacy.get())) {
            delete(RedisKeyConstants.REFRESH_TOKEN + userId);
        }
    }

    public void deleteRefreshToken(UUID userId) {
        deleteAllRefreshTokens(userId);
    }

    public void deleteAllRefreshTokens(UUID userId) {
        delete(RedisKeyConstants.REFRESH_TOKEN + userId);
        Set<Object> tokens = redisTemplate.opsForSet().members(RedisKeyConstants.USER_TOKENS + userId);
        if (tokens != null) {
            for (Object t : tokens) {
                if (t != null) {
                    delete(RedisKeyConstants.REFRESH_TOKEN + userId + ":" + t.toString());
                }
            }
        }
        delete(RedisKeyConstants.USER_TOKENS + userId);
    }

    // ── Password Reset Token ─────────────────────────────────────────────────

    public void savePasswordResetToken(String tokenHash, UUID userId) {
        set(RedisKeyConstants.PASSWORD_RESET_TOKEN + tokenHash, userId.toString(), passwordResetTokenTtl);
    }

    public Optional<UUID> getPasswordResetUserId(String tokenHash) {
        return get(RedisKeyConstants.PASSWORD_RESET_TOKEN + tokenHash)
                .map(Object::toString)
                .map(UUID::fromString);
    }

    public void deletePasswordResetToken(String tokenHash) {
        delete(RedisKeyConstants.PASSWORD_RESET_TOKEN + tokenHash);
    }

    // ── Rate Limit ────────────────────────────────────────────────────────────

    public boolean isRateLimited(String identifier) {
        String key = RedisKeyConstants.RATE_LIMIT + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(rateLimitWindow));
        }
        return count != null && count > rateLimitMax;
    }

    public long getRateLimitRemaining(String identifier) {
        String key = RedisKeyConstants.RATE_LIMIT + identifier;
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null) return rateLimitMax;
        return Math.max(0, rateLimitMax - Long.parseLong(raw.toString()));
    }

    // ── SSE Connected Users ───────────────────────────────────────────────────

    public void addSseUser(String userId) {
        redisTemplate.opsForSet().add(RedisKeyConstants.SSE_CONNECTED_USERS, userId);
    }

    public void removeSseUser(String userId) {
        redisTemplate.opsForSet().remove(RedisKeyConstants.SSE_CONNECTED_USERS, userId);
    }

    public boolean isSseUserConnected(String userId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(RedisKeyConstants.SSE_CONNECTED_USERS, userId));
    }

    public Set<Object> getAllSseUsers() {
        return redisTemplate.opsForSet().members(RedisKeyConstants.SSE_CONNECTED_USERS);
    }
}
