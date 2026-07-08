package project.lms_rikkei_edu.modules.chat.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMemberResponse;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatRoomResponse;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomMemberEntity;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    // ChatRoom

    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "groupName", source = "group.name")
    @Mapping(target = "lastMessage", ignore = true)
    @Mapping(target = "unreadCount", ignore = true)
    @Mapping(target = "members", ignore = true)
    ChatRoomResponse toRoomResponse(ChatRoomEntity room);

    // ChatRoomMember

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    ChatMemberResponse toMemberResponse(ChatRoomMemberEntity member);

    List<ChatMemberResponse> toMemberResponseList(List<ChatRoomMemberEntity> members);

    // ChatMessage

    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "senderId", source = "sender.id")
    @Mapping(target = "senderName", source = "sender.fullName")
    @Mapping(target = "senderAvatar", source = "sender.avatarUrl")
    @Mapping(target = "replyToId", source = "replyTo.id")
    @Mapping(target = "replyToContent", source = "replyTo.content")
    @Mapping(target = "replyToAttachmentName", expression = "java(message.getReplyTo() != null ? message.getReplyTo().getAttachmentName() : null)")
    @Mapping(target = "replyToSenderName", expression = "java(message.getReplyTo() != null " +
            "&& message.getReplyTo().getSender() != null " +
            "? message.getReplyTo().getSender().getFullName() : null)")
    @Mapping(target = "reactions", ignore = true)
    ChatMessageResponse toMessageResponse(ChatMessageEntity message);

    List<ChatMessageResponse> toMessageResponseList(List<ChatMessageEntity> messages);
}