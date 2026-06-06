package project.lms_rikkei_edu.infrastructure.redis;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CacheTest {
    @Cacheable(value = "test-redis", key = "#id")
    public String getData(Long id) {

        System.out.println("🔥 CALL METHOD - NOT FROM CACHE");

        return "DATA_" + id;
    }
}
