package project.lms_rikkei_edu.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/test-redis")
@RequiredArgsConstructor
public class RedisTest {
    private final RedisService redisService;
    private final CacheTest cacheTest;
    private final Map<String, SseEmitter> clients = new ConcurrentHashMap<>();

    @GetMapping("/set")
    public String set() {
        redisService.set("test:key", "hello redis", 60);
        return "OK";
    }

    @GetMapping("/get")
    public Object get() {
        return redisService.get("test:key");
    }

    @GetMapping("/refresh")
    public String testRefresh() {
        UUID userId = UUID.randomUUID();

        redisService.saveRefreshToken(userId, "token-123");

        return redisService.getRefreshToken(userId).orElse("null");
    }

    @GetMapping("/blacklist")
    public String testBlacklist() {

        String jti = "abc123";

        redisService.blacklistAccessToken(jti, new Date(System.currentTimeMillis() + 60000));

        return String.valueOf(redisService.isAccessTokenBlacklisted(jti));
    }

    @GetMapping("/check")
    public String check() {

        String jti = "abc123";

        return "RESULT = " + redisService.isAccessTokenBlacklisted(jti);
    }

    @GetMapping("/rate-limit")
    public String testRateLimit() {

        String ip = "127.0.0.1";

        boolean limited = redisService.isRateLimited(ip);

        return limited ? "BLOCKED" : "OK";
    }

    @GetMapping("/{id}")
    public String test(@PathVariable Long id) {
        return cacheTest.getData(id);
    }

}
