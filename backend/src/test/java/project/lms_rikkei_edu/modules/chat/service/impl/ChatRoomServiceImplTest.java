package project.lms_rikkei_edu.modules.chat.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMemberResponse;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatRoomResponse;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomMemberEntity;
import project.lms_rikkei_edu.modules.chat.exception.ChatAccessDeniedException;
import project.lms_rikkei_edu.modules.chat.exception.ChatMessageNotFoundException;
import project.lms_rikkei_edu.modules.chat.exception.ChatRoomNotFoundException;
import project.lms_rikkei_edu.modules.chat.mapper.ChatMapper;
import project.lms_rikkei_edu.modules.chat.repository.ChatMessageRepository;
import project.lms_rikkei_edu.modules.chat.repository.ChatMessageReactionRepository;
import project.lms_rikkei_edu.modules.chat.repository.ChatRoomMemberRepository;
import project.lms_rikkei_edu.modules.chat.repository.ChatRoomRepository;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceImplTest {

    @Mock
    private ChatRoomRepository roomRepo;

    @Mock
    private ChatRoomMemberRepository memberRepo;

    @Mock
    private ChatMessageRepository messageRepo;

    @Mock
    private ChatMessageReactionRepository reactionRepo;

    @Mock
    private ChatMapper chatMapper;

    @InjectMocks
    private ChatRoomServiceImpl service;

    private ChatRoomEntity room(UUID id) {
        ChatRoomEntity r = new ChatRoomEntity();
        r.setId(id);
        r.setName("Room");
        r.setActive(true);
        return r;
    }

    private UserEntity user(UUID id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setFullName("User");
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

    // ── getMyRooms ────────────────────────────────────────

    @Test
    void shouldGetMyRooms() {
        UUID userId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        ChatRoomResponse response = ChatRoomResponse.builder()
                .id(room.getId())
                .name("Room")
                .build();

        when(roomRepo.findAllByMemberId(userId)).thenReturn(List.of(room));
        when(chatMapper.toRoomResponse(room)).thenReturn(response);
        when(messageRepo.findLastMessageByRoomId(room.getId())).thenReturn(Optional.empty());
        when(memberRepo.findByRoomIdAndUserId(room.getId(), userId)).thenReturn(Optional.empty());
        when(messageRepo.countUnreadMessages(any(), any())).thenReturn(0);

        List<ChatRoomResponse> result = service.getMyRooms(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(room.getId());
    }

    @Test
    void shouldGetMyRoomsWithLastMessage() {
        UUID userId = UUID.randomUUID();
        UserEntity sender = user(UUID.randomUUID());
        ChatRoomEntity room = room(UUID.randomUUID());
        ChatMessageEntity lastMsg = message(UUID.randomUUID(), room, sender);
        ChatMessageResponse msgResponse = ChatMessageResponse.builder()
                .id(lastMsg.getId())
                .content("Last message")
                .build();
        ChatRoomResponse response = ChatRoomResponse.builder()
                .id(room.getId())
                .build();

        when(roomRepo.findAllByMemberId(userId)).thenReturn(List.of(room));
        when(chatMapper.toRoomResponse(room)).thenReturn(response);
        when(messageRepo.findLastMessageByRoomId(room.getId())).thenReturn(Optional.of(lastMsg));
        when(chatMapper.toMessageResponse(lastMsg)).thenReturn(msgResponse);
        when(memberRepo.findByRoomIdAndUserId(room.getId(), userId)).thenReturn(Optional.empty());
        when(messageRepo.countUnreadMessages(any(), any())).thenReturn(0);

        List<ChatRoomResponse> result = service.getMyRooms(userId);

        assertThat(result.get(0).getLastMessage()).isNotNull();
        assertThat(result.get(0).getLastMessage().getContent()).isEqualTo("Last message");
    }

    @Test
    void shouldReturnEmptyRoomsWhenNone() {
        UUID userId = UUID.randomUUID();
        when(roomRepo.findAllByMemberId(userId)).thenReturn(List.of());

        List<ChatRoomResponse> result = service.getMyRooms(userId);

        assertThat(result).isEmpty();
    }

    // ── getRoomDetail ─────────────────────────────────────

    @Test
    void shouldGetRoomDetail() {
        UUID userId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());
        ChatRoomResponse response = ChatRoomResponse.builder()
                .id(room.getId())
                .build();
        ChatRoomMemberEntity member = new ChatRoomMemberEntity();
        member.setId(UUID.randomUUID());
        member.setUser(user(userId));
        ChatMemberResponse memberResponse = ChatMemberResponse.builder()
                .userId(userId)
                .build();

        when(roomRepo.findById(room.getId())).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), userId)).thenReturn(true);
        when(chatMapper.toRoomResponse(room)).thenReturn(response);
        when(messageRepo.findLastMessageByRoomId(room.getId())).thenReturn(Optional.empty());
        when(memberRepo.findByRoomIdAndUserId(room.getId(), userId)).thenReturn(Optional.of(member));
        when(messageRepo.countUnreadMessages(any(), any())).thenReturn(0);
        when(memberRepo.findAllByRoomId(room.getId())).thenReturn(List.of(member));
        when(chatMapper.toMemberResponseList(any())).thenReturn(List.of(memberResponse));

        ChatRoomResponse result = service.getRoomDetail(room.getId(), userId);

        assertThat(result.getId()).isEqualTo(room.getId());
        assertThat(result.getMembers()).hasSize(1);
    }

    @Test
    void shouldThrowWhenGetRoomDetailNonMember() {
        UUID userId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());

        when(roomRepo.findById(room.getId())).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), userId)).thenReturn(false);

        assertThrows(ChatAccessDeniedException.class,
                () -> service.getRoomDetail(room.getId(), userId));
    }

    @Test
    void shouldThrowWhenGetRoomDetailNotFound() {
        UUID roomId = UUID.randomUUID();
        when(roomRepo.findById(roomId)).thenReturn(Optional.empty());

        assertThrows(ChatRoomNotFoundException.class,
                () -> service.getRoomDetail(roomId, UUID.randomUUID()));
    }

    // ── createRoomForGroup ────────────────────────────────

    @Test
    void shouldCreateRoomForGroup() {
        StudyGroupEntity group = new StudyGroupEntity();
        group.setId(UUID.randomUUID());
        group.setName("Group A");

        UserEntity instructor = user(UUID.randomUUID());
        ChatRoomEntity saved = room(UUID.randomUUID());
        saved.setGroup(group);

        when(roomRepo.existsByGroupId(group.getId())).thenReturn(false);
        when(roomRepo.save(any())).thenReturn(saved);

        ChatRoomEntity result = service.createRoomForGroup(group, instructor);

        assertThat(result.getId()).isNotNull();
        verify(roomRepo).save(any(ChatRoomEntity.class));
    }

    @Test
    void shouldThrowWhenRoomForGroupAlreadyExists() {
        StudyGroupEntity group = new StudyGroupEntity();
        group.setId(UUID.randomUUID());

        when(roomRepo.existsByGroupId(group.getId())).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.createRoomForGroup(group, user(UUID.randomUUID())));
    }

    // ── addMember ─────────────────────────────────────────

    @Test
    void shouldAddMember() {
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity newUser = user(UUID.randomUUID());

        when(roomRepo.findById(room.getId())).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), newUser.getId())).thenReturn(false);

        service.addMember(room.getId(), newUser, ChatRoomMemberEntity.MemberRole.MEMBER);

        verify(memberRepo).save(any(ChatRoomMemberEntity.class));
    }

    @Test
    void shouldSkipWhenMemberAlreadyExists() {
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity existingUser = user(UUID.randomUUID());

        when(roomRepo.findById(room.getId())).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(room.getId(), existingUser.getId())).thenReturn(true);

        service.addMember(room.getId(), existingUser, ChatRoomMemberEntity.MemberRole.MEMBER);

        verify(memberRepo, never()).save(any());
    }

    // ── markAsRead ────────────────────────────────────────

    @Test
    void shouldMarkAsRead() {
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity user = user(UUID.randomUUID());
        ChatMessageEntity msg = message(UUID.randomUUID(), room, user);

        ChatRoomMemberEntity member = new ChatRoomMemberEntity();
        member.setId(UUID.randomUUID());
        member.setRoom(room);
        member.setUser(user);

        when(memberRepo.findByRoomIdAndUserId(room.getId(), user.getId())).thenReturn(Optional.of(member));
        when(messageRepo.findById(msg.getId())).thenReturn(Optional.of(msg));

        service.markAsRead(room.getId(), msg.getId(), user.getId());

        assertThat(member.getLastReadMessage()).isEqualTo(msg);
        verify(memberRepo).save(member);
    }

    @Test
    void shouldThrowWhenMarkAsReadMemberNotFound() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepo.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThrows(ChatRoomNotFoundException.class,
                () -> service.markAsRead(roomId, UUID.randomUUID(), userId));
    }

    @Test
    void shouldThrowWhenMarkAsReadMessageNotFound() {
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity user = user(UUID.randomUUID());

        ChatRoomMemberEntity member = new ChatRoomMemberEntity();
        member.setId(UUID.randomUUID());
        member.setRoom(room);
        member.setUser(user);

        UUID messageId = UUID.randomUUID();

        when(memberRepo.findByRoomIdAndUserId(room.getId(), user.getId())).thenReturn(Optional.of(member));
        when(messageRepo.findById(messageId)).thenReturn(Optional.empty());

        assertThrows(ChatMessageNotFoundException.class,
                () -> service.markAsRead(room.getId(), messageId, user.getId()));
    }

    // ── validateMember ────────────────────────────────────

    @Test
    void shouldValidateMember() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

        service.validateMember(roomId, userId);
    }

    @Test
    void shouldThrowWhenValidateMemberFails() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);

        assertThrows(ChatAccessDeniedException.class,
                () -> service.validateMember(roomId, userId));
    }

    // ── removeMember ──────────────────────────────────────

    @Test
    void shouldRemoveMember() {
        ChatRoomEntity room = room(UUID.randomUUID());
        UserEntity user = user(UUID.randomUUID());
        ChatRoomMemberEntity member = new ChatRoomMemberEntity();
        member.setId(UUID.randomUUID());
        member.setRoom(room);
        member.setUser(user);

        when(memberRepo.findByRoomIdAndUserId(room.getId(), user.getId())).thenReturn(Optional.of(member));

        service.removeMember(room.getId(), user.getId());

        verify(memberRepo).delete(member);
    }

    @Test
    void shouldIgnoreWhenRemoveMemberNotFound() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepo.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        service.removeMember(roomId, userId);

        verify(memberRepo, never()).delete(any());
    }

    @Test
    void shouldDeleteRoomForGroupInForeignKeySafeOrder() {
        UUID groupId = UUID.randomUUID();
        ChatRoomEntity room = room(UUID.randomUUID());

        when(roomRepo.findByGroupId(groupId)).thenReturn(Optional.of(room));

        service.deleteRoomForGroup(groupId);

        InOrder inOrder = inOrder(memberRepo, reactionRepo, messageRepo, roomRepo);
        inOrder.verify(memberRepo).clearLastReadMessagesByRoomId(room.getId());
        inOrder.verify(reactionRepo).deleteAllByRoomId(room.getId());
        inOrder.verify(messageRepo).deleteAllByRoomId(room.getId());
        inOrder.verify(memberRepo).deleteAllByRoomId(room.getId());
        inOrder.verify(roomRepo).delete(room);
    }
}
