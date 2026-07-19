package project.lms_rikkei_edu.modules.chat.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import project.lms_rikkei_edu.common.security.UserPrincipal;
import project.lms_rikkei_edu.modules.chat.dto.request.SendMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;
import project.lms_rikkei_edu.modules.chat.service.ChatMessageService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.enums.UserStatus;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketControllerTest {

    @Test
    void sendMessageDelegatesToChatMessageService() {
        ChatMessageService chatMessageService = mock(ChatMessageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatWebSocketController controller = new ChatWebSocketController(chatMessageService, userRepository);
        UUID roomId = UUID.randomUUID();
        UserEntity sender = user(UserRole.STUDENT);
        UserPrincipal principal = new UserPrincipal(sender);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SendMessageRequest request = SendMessageRequest.builder()
                .messageType(ChatMessageEntity.MessageType.TEXT)
                .content("Hello")
                .build();
        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(UUID.randomUUID())
                .content("Hello")
                .build();
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(chatMessageService.sendMessage(roomId, request, sender)).thenReturn(response);

        ChatMessageResponse result = controller.sendMessage(roomId, request, auth);

        assertThat(result).isSameAs(response);
        verify(chatMessageService).sendMessage(roomId, request, sender);
    }

    private UserEntity user(UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("student@example.com");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
