package project.lms_rikkei_edu.infrastructure.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import project.lms_rikkei_edu.common.security.CustomUserDetailsService;
import project.lms_rikkei_edu.common.security.JwtService;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.infrastructure.redis.RedisService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthInterceptorTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final RedisService redisService = mock(RedisService.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(jwtService, userDetailsService, redisService);

    @Test
    void preSendReturnsMessageWhenCommandIsNotConnect() {
        Message<?> message = message(StompCommand.SEND, null);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void preSendThrowsWhenAuthorizationHeaderIsMissing() {
        Message<?> message = message(StompCommand.CONNECT, null);

        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void preSendThrowsWhenTokenIsBlacklisted() {
        Message<?> message = message(StompCommand.CONNECT, "Bearer token");
        when(jwtService.extractJti("token")).thenReturn("jti");
        when(redisService.isAccessTokenBlacklisted("jti")).thenReturn(true);

        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void preSendAuthenticatesConnectMessage() {
        Message<?> message = message(StompCommand.CONNECT, "Bearer token");
        UserPrincipal principal = principal();
        when(jwtService.extractJti("token")).thenReturn("jti");
        when(redisService.isAccessTokenBlacklisted("jti")).thenReturn(false);
        when(jwtService.extractUsername("token")).thenReturn("instructor@example.com");
        when(userDetailsService.loadUserByUsername("instructor@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid("token", principal)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("instructor@example.com");
    }

    private Message<?> message(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UserPrincipal principal() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("instructor@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.INSTRUCTOR);
        user.setStatus(UserStatus.ACTIVE);
        return new UserPrincipal(user);
    }
}
