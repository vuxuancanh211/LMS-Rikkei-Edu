package project.lms_rikkei_edu.modules.chat.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {

    @Mock
    private ChatMessageRepository messageRepo;

    @Mock
    private ChatMessageReactionRepository reactionRepo;

    @Mock
    private ChatRoomRepository roomRepo;

    @Mock
    private ChatRoomMemberRepository memberRepo;

    @Mock
    private ChatMapper chatMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatMessageServiceImpl service;

    @Captor
    private ArgumentCaptor<StompPayload> payloadCaptor;

    private ChatRoomEntity room(UUID id) {
        ChatRoomEntity r = new ChatRoomEntity();
        r.setId(id);
        return r;
    }

    private UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        return u;
    }

    private ChatMessageEntity message(UUID id, ChatRoomEntity room, UserEntity sender) {
        ChatMessageEntity m = new ChatMessageEntity();
        m.setId(id);
        m.setRoom(room);
        m.setSender(sender);
        m.setCreatedAt(OffsetDateTime.now());
        return m;
    }

    // ── sendMessage ───────────────────────────────────────

    @Test
    void shouldRejectReplyToMessageFromDifferentRoom() {
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        ChatRoomEntity room = room(roomId);
        UserEntity sender = user(senderId);

        ChatMessageEntity replyMessage = message(replyId, room(otherRoomId), sender);
        replyMessage.setRoom(room(otherRoomId));

        SendMessageRequest request = SendMessageRequest.builder()
                .content("reply")
                .messageType(ChatMessageEntity.MessageType.TEXT)
                .replyToId(replyId)
                .build();

        when(roomRepo.findById(roomId)).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(roomId, senderId)).thenReturn(true);
        when(messageRepo.findById(replyId)).thenReturn(Optional.of(replyMessage));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendMessage(roomId, request, sender));

        assertThat(ex.getMessage()).contains("phải thuộc cùng phòng chat này");
    }

    @Test
    void shouldSendTextMessage() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        ChatRoomEntity room = room(roomId);
        UserEntity sender = user(senderId);

        SendMessageRequest request = SendMessageRequest.builder()
                .content("Hello")
                .messageType(ChatMessageEntity.MessageType.TEXT)
                .build();

        ChatMessageEntity saved = message(UUID.randomUUID(), room, sender);
        saved.setContent("Hello");
        saved.setMessageType(ChatMessageEntity.MessageType.TEXT);

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(saved.getId())
                .content("Hello")
                .build();

        when(roomRepo.findById(roomId)).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(roomId, senderId)).thenReturn(true);
        when(messageRepo.save(any())).thenReturn(saved);
        when(chatMapper.toMessageResponse(saved)).thenReturn(response);
        when(reactionRepo.countReactionsByMessageId(saved.getId())).thenReturn(Collections.emptyList());

        ChatMessageResponse result = service.sendMessage(roomId, request, sender);

        assertThat(result.getId()).isEqualTo(saved.getId());
        verify(roomRepo).save(room);
        verify(messagingTemplate).convertAndSend(any(), any(StompPayload.class));
    }

    @Test
    void shouldSendFileMessage() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        ChatRoomEntity room = room(roomId);
        UserEntity sender = user(senderId);

        SendMessageRequest request = SendMessageRequest.builder()
                .content(null)
                .messageType(ChatMessageEntity.MessageType.FILE)
                .attachmentUrl("https://bucket.s3.com/file.pdf")
                .attachmentName("file.pdf")
                .attachmentSizeBytes(1024L)
                .build();

        ChatMessageEntity saved = message(UUID.randomUUID(), room, sender);
        saved.setAttachmentUrl("https://bucket.s3.com/file.pdf");
        saved.setMessageType(ChatMessageEntity.MessageType.FILE);

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(saved.getId())
                .attachmentUrl("https://bucket.s3.com/file.pdf")
                .build();

        when(roomRepo.findById(roomId)).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(roomId, senderId)).thenReturn(true);
        when(messageRepo.save(any())).thenReturn(saved);
        when(chatMapper.toMessageResponse(saved)).thenReturn(response);
        when(reactionRepo.countReactionsByMessageId(saved.getId())).thenReturn(Collections.emptyList());

        ChatMessageResponse result = service.sendMessage(roomId, request, sender);

        assertThat(result.getAttachmentUrl()).isEqualTo("https://bucket.s3.com/file.pdf");
        verify(messagingTemplate).convertAndSend(any(), any(StompPayload.class));
    }

    @Test
    void shouldThrowWhenTextMessageHasEmptyContent() {
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity sender = user(UUID.randomUUID());
        SendMessageRequest request = SendMessageRequest.builder()
                .content("")
                .messageType(ChatMessageEntity.MessageType.TEXT)
                .build();

        when(roomRepo.findById(room.getId())).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), sender.getId())).thenReturn(true);

        UUID roomId = room.getId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendMessage(roomId, request, sender));

        assertThat(ex.getMessage()).contains("Nội dung không được để trống");
    }

    @Test
    void shouldThrowWhenFileMessageHasNoUrl() {
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity sender = user(UUID.randomUUID());
        SendMessageRequest request = SendMessageRequest.builder()
                .messageType(ChatMessageEntity.MessageType.FILE)
                .attachmentUrl(null)
                .build();

        when(roomRepo.findById(room.getId())).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), sender.getId())).thenReturn(true);

        UUID roomId = room.getId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendMessage(roomId, request, sender));

        assertThat(ex.getMessage()).contains("File không được để trống");
    }

    @Test
    void shouldThrowWhenSenderIsNotMember() {
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity sender = user(UUID.randomUUID());
        SendMessageRequest request = SendMessageRequest.builder()
                .content("Hello")
                .messageType(ChatMessageEntity.MessageType.TEXT)
                .build();

        when(roomRepo.findById(room.getId())).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), sender.getId())).thenReturn(false);

        UUID roomId = room.getId();
        assertThrows(ChatAccessDeniedException.class,
                () -> service.sendMessage(roomId, request, sender));
    }

    @Test
    void shouldThrowWhenRoomNotFound() {
        UUID roomId = UUID.randomUUID();
        UserEntity sender = user(UUID.randomUUID());
        SendMessageRequest request = SendMessageRequest.builder()
                .content("Hello")
                .build();

        when(roomRepo.findById(roomId)).thenReturn(Optional.empty());

        assertThrows(ChatRoomNotFoundException.class,
                () -> service.sendMessage(roomId, request, sender));
    }

    // ── editMessage ───────────────────────────────────────

    @Test
    void shouldEditMessage() {
        UUID senderId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity sender = user(senderId);
        ChatMessageEntity msg = message(UUID.randomUUID(), room, sender);
        msg.setContent("Original");

        EditMessageRequest request = new EditMessageRequest("Updated");

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(msg.getId())
                .content("Updated")
                .edited(true)
                .build();

        when(messageRepo.findById(msg.getId())).thenReturn(Optional.of(msg));
        when(chatMapper.toMessageResponse(msg)).thenReturn(response);
        when(reactionRepo.countReactionsByMessageId(msg.getId())).thenReturn(Collections.emptyList());

        ChatMessageResponse result = service.editMessage(msg.getId(), request, senderId);

        assertThat(result.isEdited()).isTrue();
        assertThat(msg.getContent()).isEqualTo("Updated");
        verify(messageRepo).save(msg);
        verify(messagingTemplate).convertAndSend(any(), any(StompPayload.class));
    }

    @Test
    void shouldThrowWhenEditNotOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity owner = user(ownerId);
        ChatMessageEntity msg = message(UUID.randomUUID(), room, owner);

        EditMessageRequest request = new EditMessageRequest("Hacked");

        when(messageRepo.findById(msg.getId())).thenReturn(Optional.of(msg));

        UUID messageId = msg.getId();
        assertThrows(ChatAccessDeniedException.class,
                () -> service.editMessage(messageId, request, otherId));
    }

    @Test
    void shouldThrowWhenEditMessageNotFound() {
        UUID messageId = UUID.randomUUID();
        EditMessageRequest request = new EditMessageRequest("x");
        UUID userId = UUID.randomUUID();
        when(messageRepo.findById(messageId)).thenReturn(Optional.empty());

        assertThrows(ChatMessageNotFoundException.class,
                () -> service.editMessage(messageId, request, userId));
    }

    // ── deleteMessage ─────────────────────────────────────

    @Test
    void shouldDeleteMessage() {
        UUID senderId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity sender = user(senderId);
        ChatMessageEntity msg = message(UUID.randomUUID(), room, sender);

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(msg.getId())
                .deleted(true)
                .build();

        when(messageRepo.findById(msg.getId())).thenReturn(Optional.of(msg));
        when(chatMapper.toMessageResponse(msg)).thenReturn(response);
        when(reactionRepo.countReactionsByMessageId(msg.getId())).thenReturn(Collections.emptyList());

        service.deleteMessage(msg.getId(), senderId);

        assertThat(msg.isDeleted()).isTrue();
        assertThat(msg.getDeletedAt()).isNotNull();
        verify(messageRepo).save(msg);
        verify(messagingTemplate).convertAndSend(any(), any(StompPayload.class));
    }

    @Test
    void shouldThrowWhenDeleteNotOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity owner = user(ownerId);
        ChatMessageEntity msg = message(UUID.randomUUID(), room, owner);

        when(messageRepo.findById(msg.getId())).thenReturn(Optional.of(msg));

        UUID messageId = msg.getId();
        assertThrows(ChatAccessDeniedException.class,
                () -> service.deleteMessage(messageId, otherId));
    }

    // ── addReaction ───────────────────────────────────────

    @Test
    void shouldAddReaction() {
        UUID senderId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity sender = user(senderId);
        ChatMessageEntity msg = message(UUID.randomUUID(), room, sender);

        ReactMessageRequest request = new ReactMessageRequest("👍");

        Object[] row = new Object[]{"👍", 1L};

        when(messageRepo.findById(msg.getId())).thenReturn(Optional.of(msg));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), senderId)).thenReturn(true);
        when(reactionRepo.countReactionsByMessageId(msg.getId())).thenReturn(List.<Object[]>of(row));

        Map<String, Long> result = service.addReaction(msg.getId(), request, sender);

        assertThat(result).containsEntry("👍", 1L);
        verify(reactionRepo).save(any(ChatMessageReactionEntity.class));
        verify(messagingTemplate).convertAndSend(any(), any(StompPayload.class));
    }

    // ── removeReaction ────────────────────────────────────

    @Test
    void shouldRemoveReaction() {
        UUID senderId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity sender = user(senderId);
        ChatMessageEntity msg = message(UUID.randomUUID(), room, sender);

        when(messageRepo.findById(msg.getId())).thenReturn(Optional.of(msg));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), senderId)).thenReturn(true);
        when(reactionRepo.countReactionsByMessageId(msg.getId())).thenReturn(Collections.emptyList());

        Map<String, Long> result = service.removeReaction(msg.getId(), "👍", senderId);

        assertThat(result).isEmpty();
        verify(reactionRepo).deleteByMessageIdAndUserIdAndEmoji(msg.getId(), senderId, "👍");
        verify(messagingTemplate).convertAndSend(any(), any(StompPayload.class));
    }

    @Test
    void shouldThrowWhenReactionNonMember() {
        UUID userId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity user = user(userId);
        ChatMessageEntity msg = message(UUID.randomUUID(), room, user);

        when(messageRepo.findById(msg.getId())).thenReturn(Optional.of(msg));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), userId)).thenReturn(false);

        UUID messageId = msg.getId();
        ReactMessageRequest request = new ReactMessageRequest("👍");
        assertThrows(ChatAccessDeniedException.class,
                () -> service.addReaction(messageId, request, user));
    }

    // ── getMessages ───────────────────────────────────────

    @Test
    void shouldGetMessages() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatRoomEntity room = room(roomId);
        UserEntity sender = user(userId);
        ChatMessageEntity msg = message(UUID.randomUUID(), room, sender);

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(msg.getId())
                .content("Hi")
                .build();

        Page<ChatMessageEntity> page = new PageImpl<>(List.of(msg));

        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);
        when(messageRepo.findByRoomId(roomId, Pageable.unpaged())).thenReturn(page);
        when(chatMapper.toMessageResponse(msg)).thenReturn(response);
        when(reactionRepo.countReactionsByMessageId(msg.getId())).thenReturn(Collections.emptyList());

        Page<ChatMessageResponse> result = service.getMessages(roomId, userId, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("Hi");
    }

    @Test
    void shouldThrowWhenGetMessagesNonMember() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);

        Pageable pageable = Pageable.unpaged();
        assertThrows(ChatAccessDeniedException.class,
                () -> service.getMessages(roomId, userId, pageable));
    }

    // ── broadcast payload verification ────────────────────

    @Test
    void shouldBroadcastChatMessageEvent() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        ChatRoomEntity room = room(roomId);
        UserEntity sender = user(senderId);

        SendMessageRequest request = SendMessageRequest.builder()
                .content("Hello")
                .messageType(ChatMessageEntity.MessageType.TEXT)
                .build();

        ChatMessageEntity saved = message(UUID.randomUUID(), room, sender);
        saved.setContent("Hello");

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(saved.getId())
                .content("Hello")
                .build();

        when(roomRepo.findById(roomId)).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(roomId, senderId)).thenReturn(true);
        when(messageRepo.save(any())).thenReturn(saved);
        when(chatMapper.toMessageResponse(saved)).thenReturn(response);
        when(reactionRepo.countReactionsByMessageId(saved.getId())).thenReturn(Collections.emptyList());

        service.sendMessage(roomId, request, sender);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat." + roomId), payloadCaptor.capture());
        StompPayload payload = payloadCaptor.getValue();

        assertThat(payload.getEvent()).isEqualTo("CHAT_MESSAGE");
        assertThat(payload.getRoomId()).isEqualTo(roomId);
        assertThat(payload.getMessage()).isEqualTo(response);
    }
}
