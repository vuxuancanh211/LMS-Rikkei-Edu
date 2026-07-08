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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.chat.repository.ChatRoomMemberRepository;

import java.security.Principal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompSubscribeInterceptor implements ChannelInterceptor {

    private final ChatRoomMemberRepository memberRepo;

    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class); // ← đổi chỗ này

        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/chat.")) {
            return message;
        }

        UUID userId = extractUserId(accessor.getUser());
        if (userId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        try {
            UUID roomId = UUID.fromString(destination.substring("/topic/chat.".length()));
            if (!memberRepo.existsByRoomIdAndUserId(roomId, userId)) {
                log.warn("User {} không phải member của room {}, deny subscribe", userId, roomId);
                throw new AccessDeniedException("Bạn không phải thành viên của phòng chat này");
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid roomId in destination: {}", destination);
            throw new AccessDeniedException("Invalid destination");
        }
        return message;
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.getId();
        }
        return null;
    }
}
