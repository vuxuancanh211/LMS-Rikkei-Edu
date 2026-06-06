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
        String key = RedisKeyConstants.REFRESH_TOKEN + userId;
        redisTemplate.opsForValue().set(key, tokenHash, Duration.ofSeconds(refreshTokenTtl));
    }

    public Optional<String> getRefreshToken(UUID userId) {
        return get(RedisKeyConstants.REFRESH_TOKEN + userId).map(Object::toString);
    }

    public void deleteRefreshToken(UUID userId) {
        delete(RedisKeyConstants.REFRESH_TOKEN + userId);
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