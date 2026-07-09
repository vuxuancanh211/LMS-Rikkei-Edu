package project.lms_rikkei_edu.modules.chat.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.lms_rikkei_edu.modules.chat.dto.request.EditMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.ReactMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.SendMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.util.Map;
import java.util.UUID;

public interface ChatMessageService {

    Page<ChatMessageResponse> getMessages(UUID roomId, UUID userId, Pageable pageable);

    ChatMessageResponse sendMessage(UUID roomId, SendMessageRequest request, UserEntity sender);

    ChatMessageResponse editMessage(UUID messageId, EditMessageRequest request, UUID userId);

    void deleteMessage(UUID messageId, UUID userId);

    Map<String, Long> addReaction(UUID messageId, ReactMessageRequest request, UserEntity user);
    Map<String, Long> removeReaction(UUID messageId, String emoji, UUID userId);
}
