package project.lms_rikkei_edu.modules.chat.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.chat.dto.request.EditMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.ReactMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.request.SendMessageRequest;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;
import project.lms_rikkei_edu.modules.chat.dto.ws.StompPayload;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageReactionEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomEntity;
import project.lms_rikkei_edu.modules.chat.exception.ChatAccessDeniedException;
import project.lms_rikkei_edu.modules.chat.exception.ChatMessageNotFoundException;
import project.lms_rikkei_edu.modules.chat.exception.ChatRoomNotFoundException;
import project.lms_rikkei_edu.modules.chat.mapper.ChatMapper;
import project.lms_rikkei_edu.modules.chat.repository.ChatMessageReactionRepository;
import project.lms_rikkei_edu.modules.chat.repository.ChatMessageRepository;
import project.lms_rikkei_edu.modules.chat.repository.ChatRoomMemberRepository;
import project.lms_rikkei_edu.modules.chat.repository.ChatRoomRepository;
import project.lms_rikkei_edu.modules.chat.service.ChatMessageService;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final ZoneOffset APP_ZONE = ZoneOffset.UTC;

    private final ChatMessageRepository messageRepo;
    private final ChatMessageReactionRepository reactionRepo;
    private final ChatRoomRepository roomRepo;
    private final ChatRoomMemberRepository memberRepo;
    private final ChatMapper chatMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public Page<ChatMessageResponse> getMessages(
            UUID roomId, UUID userId, Pageable pageable) {
        validateMember(roomId, userId);
        return messageRepo.findByRoomId(roomId, pageable)
                .map(this::buildMessageResponse);
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(
            UUID roomId, SendMessageRequest request, UserEntity sender) {

        ChatRoomEntity room = findRoomById(roomId);
        validateMember(roomId, sender.getId());

        if (request.getMessageType() == ChatMessageEntity.MessageType.TEXT
                && !StringUtils.hasText(request.getContent())) {
            throw new BusinessException("Nội dung không được để trống");
        }
        if (request.getMessageType() == ChatMessageEntity.MessageType.FILE
                && !StringUtils.hasText(request.getAttachmentUrl())) {
            throw new BusinessException("File không được để trống");
        }

        ChatMessageEntity.ChatMessageEntityBuilder builder = ChatMessageEntity.builder()
                .room(room)
                .sender(sender)
                .messageType(request.getMessageType())
                .content(request.getContent())
                .attachmentUrl(request.getAttachmentUrl())
                .attachmentName(request.getAttachmentName())
                .attachmentSizeBytes(request.getAttachmentSizeBytes());

        if (request.getReplyToId() != null) {
            ChatMessageEntity replyTo = messageRepo.findById(request.getReplyToId())
                    .orElseThrow(() -> new ChatMessageNotFoundException(request.getReplyToId()));
            if (!replyTo.getRoom().getId().equals(room.getId())) {
                throw new BusinessException("Tin nhắn được reply phải thuộc cùng phòng chat này");
            }
            builder.replyTo(replyTo);
        }

        ChatMessageEntity saved = messageRepo.save(builder.build());

        room.setLastMessageAt(saved.getCreatedAt());
        roomRepo.save(room);

        ChatMessageResponse response = buildMessageResponse(saved);

        broadcast(roomId, StompPayload.builder()
                .event("CHAT_MESSAGE")
                .roomId(roomId)
                .message(response)
                .timestamp(OffsetDateTime.now(APP_ZONE))
                .build());

        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponse editMessage(
            UUID messageId, EditMessageRequest request, UUID userId) {

        ChatMessageEntity message = findMessageById(messageId);
        validateOwner(message, userId);

        message.setContent(request.getContent());
        message.setEdited(true);
        message.setEditedAt(OffsetDateTime.now(APP_ZONE));
        messageRepo.save(message);

        ChatMessageResponse response = buildMessageResponse(message);

        broadcast(message.getRoom().getId(), StompPayload.builder()
                .event("MESSAGE_EDITED")
                .roomId(message.getRoom().getId())
                .message(response)
                .timestamp(OffsetDateTime.now(APP_ZONE))
                .build());

        return response;
    }

    @Override
    @Transactional
    public void deleteMessage(UUID messageId, UUID userId) {
        ChatMessageEntity message = findMessageById(messageId);
        validateOwner(message, userId);

        message.setDeleted(true);
        message.setDeletedAt(OffsetDateTime.now(APP_ZONE));
        messageRepo.save(message);

        broadcast(message.getRoom().getId(), StompPayload.builder()
                .event("MESSAGE_DELETED")
                .roomId(message.getRoom().getId())
                .message(buildMessageResponse(message))
                .timestamp(OffsetDateTime.now(APP_ZONE))
                .build());
    }

    @Override
    @Transactional
    public Map<String, Long> addReaction(
            UUID messageId, ReactMessageRequest request, UserEntity user) {

        ChatMessageEntity message = findMessageById(messageId);
        validateMember(message.getRoom().getId(), user.getId());

        reactionRepo.save(ChatMessageReactionEntity.builder()
                .message(message)
                .user(user)
                .emoji(request.getEmoji())
                .build());

        Map<String, Long> reactions = buildReactions(messageId);

        broadcast(message.getRoom().getId(), StompPayload.builder()
                .event("REACTION_ADDED")
                .roomId(message.getRoom().getId())
                .messageId(messageId)
                .emoji(request.getEmoji())
                .reactions(reactions)
                .timestamp(OffsetDateTime.now(APP_ZONE))
                .build());

        return reactions;
    }

    @Override
    @Transactional
    public Map<String, Long> removeReaction(UUID messageId, String emoji, UUID userId) {
        ChatMessageEntity message = findMessageById(messageId);
        validateMember(message.getRoom().getId(), userId);

        reactionRepo.deleteByMessageIdAndUserIdAndEmoji(messageId, userId, emoji);

        Map<String, Long> reactions = buildReactions(messageId);

        broadcast(message.getRoom().getId(), StompPayload.builder()
                .event("REACTION_REMOVED")
                .roomId(message.getRoom().getId())
                .messageId(messageId)
                .emoji(emoji)
                .reactions(reactions)
                .timestamp(OffsetDateTime.now(APP_ZONE))
                .build());

        return reactions;
    }

    // ── Internal ──────────────────────────────────────────

    private ChatMessageResponse buildMessageResponse(ChatMessageEntity message) {
        ChatMessageResponse response = chatMapper.toMessageResponse(message);
        response.setReactions(buildReactions(message.getId()));
        return response;
    }

    private Map<String, Long> buildReactions(UUID messageId) {
        return reactionRepo.countReactionsByMessageId(messageId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]));
    }

    private void broadcast(UUID roomId, StompPayload payload) {
        messagingTemplate.convertAndSend(
                "/topic/chat." + roomId, payload);
    }

    private ChatRoomEntity findRoomById(UUID roomId) {
        return roomRepo.findById(roomId)
                .orElseThrow(() -> new ChatRoomNotFoundException(roomId));
    }

    private ChatMessageEntity findMessageById(UUID messageId) {
        return messageRepo.findById(messageId)
                .orElseThrow(() -> new ChatMessageNotFoundException(messageId));
    }

    private void validateMember(UUID roomId, UUID userId) {
        if (!memberRepo.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ChatAccessDeniedException("Bạn không phải thành viên của phòng chat này");
        }
    }

    private void validateOwner(ChatMessageEntity message, UUID userId) {
        if (!message.getSender().getId().equals(userId)) {
            throw new ChatAccessDeniedException("Bạn không có quyền thực hiện thao tác này");
        }
    }
}
