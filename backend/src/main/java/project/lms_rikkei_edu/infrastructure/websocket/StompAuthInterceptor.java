package project.lms_rikkei_edu.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.common.security.CustomUserDetailsService;
import project.lms_rikkei_edu.common.security.JwtService;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RedisService redisService;

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class); // ← đổi chỗ này

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("Missing or invalid token");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            String jti = jwtService.extractJti(token);
            if (jti != null && redisService.isAccessTokenBlacklisted(jti)) {
                throw new BadCredentialsException("Token is blacklisted");
            }

            String email = jwtService.extractUsername(token);
            UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(email);

            if (!jwtService.isTokenValid(token, principal)) {
                throw new BadCredentialsException("Token is invalid or expired");
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());

            accessor.setUser(auth);
            log.debug("STOMP authenticated: {}", email);

        } catch (RuntimeException e) {
            log.warn("STOMP auth failed: {}", e.getMessage());
            throw new BadCredentialsException("Authentication failed");
        }

        return message;
    }
}
