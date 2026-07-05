package project.lms_rikkei_edu.common.constants;

public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    public static final String COURSE_DRAFT          = "course:draft:";     // course:draft:{instructorId}:{courseId} — TTL 1h, auto-save nháp

    public static final String COURSE_STRUCTURE      = "course:structure:"; // course:structure:{courseId} — TTL 30m, cache chapter/lesson structure

    public static final String COURSE_CREATE_RL      = "rl:course:create:"; // rl:course:create:{instructorId} — TTL 1h, rate limit tạo khóa học

    public static final String SLUG_CHECK            = "course:slug:";      // course:slug:{slug} — TTL 5m, cache kết quả check slug

    public static final String ACCESS_TOKEN_BLACKLIST = "auth:blacklist:";   // {jti}
    public static final String REFRESH_TOKEN         = "auth:refresh:";      // {userId}
    public static final String PASSWORD_RESET_TOKEN  = "auth:password-reset:"; // {tokenHash}
    public static final String RATE_LIMIT            = "rl:";                // {ip} hoặc {userId}
    public static final String AUTH_RATE_LIMIT_LOGIN = "auth:login:";        // rl:auth:login:{email}
    public static final String AUTH_RATE_LIMIT_FORGOT_PASSWORD = "auth:forgot-password:"; // rl:auth:forgot-password:{email}
    public static final String COURSE_DETAIL         = "course:detail:";     // dùng với @Cacheable("course-detail")
    public static final String SSE_CONNECTED_USERS   = "sse:connected";      // Set chứa userId đang kết nối SSE
    public static final String ADMIN_USERS_LIST    = "admin_users:list:";    // {hashOfQueryParams}
    public static final String ADMIN_USER_DETAIL   = "admin_user_detail:";  // {userId}
    public static final String USER_PROFILE        = "user:profile:";       // {userId}
    public static final String USER_TOKENS         = "user_tokens:";        // {userId} — Set chứa các refresh token hash
    public static final String JWT_BLOCKLIST       = "jwt_blocklist:";      // {userId} — block all access tokens của user
    public static final String CSV_IMPORT_PREVIEW  = "csv:import:";         // {token} — preview data for CSV import
    public static final String GROUP_MEMBER_CSV_IMPORT_PREVIEW = "csv:group-member-import:"; // {token}
}
