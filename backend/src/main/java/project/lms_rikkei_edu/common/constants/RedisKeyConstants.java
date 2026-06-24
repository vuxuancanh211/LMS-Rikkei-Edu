package project.lms_rikkei_edu.common.constants;

public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    public static final String ACCESS_TOKEN_BLACKLIST = "auth:blacklist:";
    // auth:blacklist:{jti}

    public static final String REFRESH_TOKEN         = "auth:refresh:";
    // auth:refresh:{userId}

    public static final String PASSWORD_RESET_TOKEN  = "auth:password-reset:";
    // auth:password-reset:{tokenHash}

    public static final String RATE_LIMIT            = "rl:";
    // rl:{ip} hoặc rl:{userId}

    public static final String AUTH_RATE_LIMIT_LOGIN = "auth:login:";
    // rl:auth:login:{email}

    public static final String AUTH_RATE_LIMIT_FORGOT_PASSWORD = "auth:forgot-password:";
    // rl:auth:forgot-password:{email}

    public static final String COURSE_DETAIL         = "course:detail:";
    // dùng với @Cacheable("course-detail")

    public static final String SSE_CONNECTED_USERS   = "sse:connected";
    // Set chứa userId đang kết nối SSE

    // Admin user management
    public static final String ADMIN_USERS_LIST    = "admin_users:list:";
    // admin_users:list:{hashOfQueryParams}

    public static final String ADMIN_USER_DETAIL   = "admin_user_detail:";
    // admin_user_detail:{userId}

    public static final String USER_PROFILE        = "user:profile:";
    // user:profile:{userId}

    public static final String USER_TOKENS         = "user_tokens:";
    // user_tokens:{userId} — Set chứa các refresh token hash

    public static final String JWT_BLOCKLIST       = "jwt_blocklist:";
    // jwt_blocklist:{userId} — block all access tokens của user

    public static final String CSV_IMPORT_PREVIEW  = "csv:import:";
    // csv:import:{token} — preview data for CSV import
}
