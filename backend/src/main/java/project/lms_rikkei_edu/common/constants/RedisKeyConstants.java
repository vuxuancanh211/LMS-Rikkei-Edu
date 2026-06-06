package project.lms_rikkei_edu.common.constants;

public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    public static final String ACCESS_TOKEN_BLACKLIST = "auth:blacklist:";
    // auth:blacklist:{jti}

    public static final String REFRESH_TOKEN         = "auth:refresh:";
    // auth:refresh:{userId}

    public static final String RATE_LIMIT            = "rl:";
    // rl:{ip} hoặc rl:{userId}

    public static final String COURSE_DETAIL         = "course:detail:";
    // dùng với @Cacheable("course-detail")

    public static final String SSE_CONNECTED_USERS   = "sse:connected";
    // Set chứa userId đang kết nối SSE
}