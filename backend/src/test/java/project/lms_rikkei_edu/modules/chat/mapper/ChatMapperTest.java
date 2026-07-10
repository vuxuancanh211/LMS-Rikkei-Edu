package project.lms_rikkei_edu.modules.chat.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMemberResponse;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatRoomResponse;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomMemberEntity;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMapperTest {

    private final ChatMapper mapper = Mappers.getMapper(ChatMapper.class);

    @Test
    void shouldMapReplyToAttachmentNameForFileReplies() {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());

        ChatMessageEntity replyTo = new ChatMessageEntity();
        replyTo.setId(UUID.randomUUID());
        replyTo.setContent(null);
        replyTo.setAttachmentName("report.pdf");

        UserEntity sender = new UserEntity();
        sender.setFullName("Alice");
        replyTo.setSender(sender);

        message.setReplyTo(replyTo);

        ChatMessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.getReplyToAttachmentName()).isEqualTo("report.pdf");
    }

    @Test
    void shouldMapMessageWithDeletedFlag() {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setDeleted(true);

        ChatMessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.isDeleted()).isTrue();
    }

    @Test
    void shouldMapMessageWithEditedFlag() {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setEdited(true);
        message.setEditedAt(OffsetDateTime.now());

        ChatMessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.isEdited()).isTrue();
        assertThat(response.getEditedAt()).isNotNull();
    }

    @Test
    void shouldMapRoomResponse() {
        StudyGroupEntity group = new StudyGroupEntity();
        group.setId(UUID.randomUUID());
        group.setName("Group A");

        ChatRoomEntity room = new ChatRoomEntity();
        room.setId(UUID.randomUUID());
        room.setName("Room A");
        room.setGroup(group);
        room.setActive(true);
        room.setCreatedAt(OffsetDateTime.now());

        ChatRoomResponse response = mapper.toRoomResponse(room);

        assertThat(response.getId()).isEqualTo(room.getId());
        assertThat(response.getName()).isEqualTo("Room A");
        assertThat(response.getGroupId()).isEqualTo(group.getId());
        assertThat(response.getGroupName()).isEqualTo("Group A");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldMapMemberResponse() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setFullName("Alice");
        user.setAvatarUrl("http://example.com/avatar.jpg");

        ChatRoomMemberEntity member = new ChatRoomMemberEntity();
        member.setId(UUID.randomUUID());
        member.setUser(user);
        member.setRole(ChatRoomMemberEntity.MemberRole.MODERATOR);
        member.setJoinedAt(OffsetDateTime.now());

        ChatMemberResponse response = mapper.toMemberResponse(member);

        assertThat(response.getUserId()).isEqualTo(user.getId());
        assertThat(response.getFullName()).isEqualTo("Alice");
        assertThat(response.getAvatarUrl()).isEqualTo("http://example.com/avatar.jpg");
        assertThat(response.getRole()).isEqualTo(ChatRoomMemberEntity.MemberRole.MODERATOR);
        assertThat(response.getJoinedAt()).isNotNull();
    }

    @Test
    void shouldMapMemberResponseList() {
        UserEntity user1 = new UserEntity();
        user1.setId(UUID.randomUUID());
        user1.setFullName("Alice");

        UserEntity user2 = new UserEntity();
        user2.setId(UUID.randomUUID());
        user2.setFullName("Bob");

        ChatRoomMemberEntity member1 = new ChatRoomMemberEntity();
        member1.setId(UUID.randomUUID());
        member1.setUser(user1);

        ChatRoomMemberEntity member2 = new ChatRoomMemberEntity();
        member2.setId(UUID.randomUUID());
        member2.setUser(user2);

        List<ChatMemberResponse> responses = mapper.toMemberResponseList(List.of(member1, member2));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getUserId()).isEqualTo(user1.getId());
        assertThat(responses.get(1).getUserId()).isEqualTo(user2.getId());
    }

    @Test
    void shouldMapReplyToSenderName() {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());

        UserEntity replySender = new UserEntity();
        replySender.setId(UUID.randomUUID());
        replySender.setFullName("Bob");

        ChatMessageEntity replyTo = new ChatMessageEntity();
        replyTo.setId(UUID.randomUUID());
        replyTo.setContent("Original message");
        replyTo.setSender(replySender);

        message.setReplyTo(replyTo);

        ChatMessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.getReplyToSenderName()).isEqualTo("Bob");
        assertThat(response.getReplyToContent()).isEqualTo("Original message");
    }

    @Test
    void shouldReturnNullFieldsWhenReplyToIsNull() {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setReplyTo(null);

        ChatMessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.getReplyToId()).isNull();
        assertThat(response.getReplyToContent()).isNull();
        assertThat(response.getReplyToAttachmentName()).isNull();
        assertThat(response.getReplyToSenderName()).isNull();
    }

    @Test
    void shouldMapMessageResponse() {
        ChatRoomEntity room = new ChatRoomEntity();
        room.setId(UUID.randomUUID());

        UserEntity sender = new UserEntity();
        sender.setId(UUID.randomUUID());
        sender.setFullName("Alice");
        sender.setAvatarUrl("http://example.com/alice.jpg");

        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setRoom(room);
        message.setSender(sender);
        message.setContent("Hello");
        message.setMessageType(ChatMessageEntity.MessageType.TEXT);
        message.setCreatedAt(OffsetDateTime.now());

        ChatMessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.getId()).isEqualTo(message.getId());
        assertThat(response.getRoomId()).isEqualTo(room.getId());
        assertThat(response.getSenderId()).isEqualTo(sender.getId());
        assertThat(response.getSenderName()).isEqualTo("Alice");
        assertThat(response.getSenderAvatar()).isEqualTo("http://example.com/alice.jpg");
        assertThat(response.getContent()).isEqualTo("Hello");
        assertThat(response.getMessageType()).isEqualTo(ChatMessageEntity.MessageType.TEXT);
        assertThat(response.getCreatedAt()).isNotNull();
    }
}
