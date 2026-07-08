package project.lms_rikkei_edu.infrastructure.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.chat.repository.ChatRoomMemberRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompSubscribeInterceptorTest {

    private final ChatRoomMemberRepository memberRepo = mock(ChatRoomMemberRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final StompSubscribeInterceptor interceptor = new StompSubscribeInterceptor(memberRepo);

    @Test
    void preSendReturnsMessageWhenCommandIsNotSubscribe() {
        Message<?> message = message(StompCommand.SEND, null, null);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void preSendReturnsMessageWhenDestinationIsNotChatTopic() {
        Message<?> message = message(StompCommand.SUBSCRIBE, "/topic/notifications", principalAuth(UUID.randomUUID()));

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void preSendThrowsWhenUserIsMissing() {
        Message<?> message = message(StompCommand.SUBSCRIBE, "/topic/chat." + UUID.randomUUID(), null);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void preSendThrowsWhenDestinationRoomIdIsInvalid() {
        Message<?> message = message(StompCommand.SUBSCRIBE, "/topic/chat.invalid", principalAuth(UUID.randomUUID()));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void preSendThrowsWhenUserIsNotRoomMember() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Message<?> message = message(StompCommand.SUBSCRIBE, "/topic/chat." + roomId, principalAuth(userId));
        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void preSendAllowsRoomMember() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Message<?> message = message(StompCommand.SUBSCRIBE, "/topic/chat." + roomId, principalAuth(userId));
        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    private Message<?> message(StompCommand command, String destination, UsernamePasswordAuthenticationToken auth) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (auth != null) {
            accessor.setUser(auth);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UsernamePasswordAuthenticationToken principalAuth(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("student@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        UserPrincipal principal = new UserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
