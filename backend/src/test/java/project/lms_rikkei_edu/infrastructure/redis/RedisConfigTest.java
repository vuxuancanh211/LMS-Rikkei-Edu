package project.lms_rikkei_edu.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class RedisConfigTest {

    @Test
    void redisConnectionFactory_usesConfiguredConnectionSettings() {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "host", "localhost");
        ReflectionTestUtils.setField(config, "port", 6379);
        ReflectionTestUtils.setField(config, "password", "secret");
        ReflectionTestUtils.setField(config, "timeout", Duration.ofSeconds(2));

        LettuceConnectionFactory factory = config.redisConnectionFactory();

        assertThat(factory).isNotNull();
        assertThat(factory.getHostName()).isEqualTo("localhost");
        assertThat(factory.getPort()).isEqualTo(6379);
        assertThat(factory.getTimeout()).isEqualTo(2000L);
    }

    @Test
    void cacheErrorHandler_ignoresCacheFailures() {
        CacheErrorHandler handler = new RedisConfig().cacheErrorHandler();
        RuntimeException exception = new RuntimeException("cache unavailable");

        assertThatNoException().isThrownBy(() -> handler.handleCacheGetError(exception, null, "key"));
        assertThatNoException().isThrownBy(() -> handler.handleCachePutError(exception, null, "key", "value"));
        assertThatNoException().isThrownBy(() -> handler.handleCacheEvictError(exception, null, "key"));
        assertThatNoException().isThrownBy(() -> handler.handleCacheClearError(exception, null));
    }
}
