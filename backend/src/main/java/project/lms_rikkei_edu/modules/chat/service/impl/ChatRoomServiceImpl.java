package project.lms_rikkei_edu.modules.chat.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.lms_rikkei_edu.common.exception.BusinessException;
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
import project.lms_rikkei_edu.modules.chat.service.ChatRoomService;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository       roomRepo;
    private final ChatRoomMemberRepository memberRepo;
    private final ChatMessageRepository    messageRepo;
    private final ChatMessageReactionRepository reactionRepo;
    private final ChatMapper               chatMapper;

    private static final OffsetDateTime EPOCH =
            OffsetDateTime.parse("1970-01-01T00:00:00Z");

    @Override
    public List<ChatRoomResponse> getMyRooms(UUID userId) {
        return roomRepo.findAllByMemberId(userId)
                .stream()
                .map(room -> buildRoomResponse(room, userId))
                .toList();
    }

    @Override
    public ChatRoomResponse getRoomDetail(UUID roomId, UUID userId) {
        ChatRoomEntity room = findRoomById(roomId);
        validateMember(roomId, userId);

        ChatRoomResponse response = buildRoomResponse(room, userId);
        response.setMembers(chatMapper.toMemberResponseList(
                memberRepo.findAllByRoomId(roomId)));
        return response;
    }

    @Override
    @Transactional
    public ChatRoomEntity createRoomForGroup(StudyGroupEntity group, UserEntity instructor) {
        if (roomRepo.existsByGroupId(group.getId())) {
            throw new BusinessException("Chat room đã tồn tại cho nhóm này");
        }

        ChatRoomEntity room = ChatRoomEntity.builder()
                .name(group.getName())
                .group(group)
                .createdBy(instructor)
                .active(true)
                .build();

        return roomRepo.save(room);
    }

    @Override
    @Transactional
    public ChatRoomEntity getOrCreateRoomForGroup(StudyGroupEntity group, UserEntity instructor) {
        ChatRoomEntity room = roomRepo.findByGroupId(group.getId())
                .orElseGet(() -> roomRepo.save(ChatRoomEntity.builder()
                        .name(group.getName())
                        .group(group)
                        .createdBy(instructor)
                        .active(true)
                        .build()));

        if (!memberRepo.existsByRoomIdAndUserId(room.getId(), instructor.getId())) {
            memberRepo.save(ChatRoomMemberEntity.builder()
                    .room(room)
                    .user(instructor)
                    .role(ChatRoomMemberEntity.MemberRole.MODERATOR)
                    .build());
        }
        return room;
    }

    @Override
    @Transactional
    public void addMember(UUID roomId, UserEntity user,
                          ChatRoomMemberEntity.MemberRole role) {
        ChatRoomEntity room = findRoomById(roomId);

        if (memberRepo.existsByRoomIdAndUserId(roomId, user.getId())) {
            return;
        }

        memberRepo.save(ChatRoomMemberEntity.builder()
                .room(room)
                .user(user)
                .role(role)
                .build());
    }

    @Override
    @Transactional
    public void markAsRead(UUID roomId, UUID messageId, UUID userId) {
        ChatRoomMemberEntity member = memberRepo
                .findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ChatRoomNotFoundException(roomId));

        if (member.getLastReadMessage() == null ||
                !member.getLastReadMessage().getId().equals(messageId)) {
            ChatMessageEntity message = messageRepo.findById(messageId)
                    .orElseThrow(() -> new ChatMessageNotFoundException(messageId));
            member.setLastReadMessage(message);
            memberRepo.save(member);
        }
    }

    @Override
    public ChatRoomEntity findRoomById(UUID roomId) {
        return roomRepo.findById(roomId)
                .orElseThrow(() -> new ChatRoomNotFoundException(roomId));
    }

    @Override
    public void validateMember(UUID roomId, UUID userId) {
        if (!memberRepo.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ChatAccessDeniedException("Bạn không phải thành viên của phòng chat này");
        }
    }

    // ── Internal ──────────────────────────────────────────

    private ChatRoomResponse buildRoomResponse(ChatRoomEntity room, UUID userId) {
        ChatRoomResponse response = chatMapper.toRoomResponse(room);

        messageRepo.findLastMessageByRoomId(room.getId())
                .ifPresent(msg -> response.setLastMessage(
                        chatMapper.toMessageResponse(msg)));

        OffsetDateTime after = memberRepo
                .findByRoomIdAndUserId(room.getId(), userId)
                .map(m -> m.getLastReadMessage() != null
                        ? m.getLastReadMessage().getCreatedAt()
                        : EPOCH)
                .orElse(EPOCH);

        response.setUnreadCount(
                messageRepo.countUnreadMessages(room.getId(), after));

        return response;
    }

    @Override
    @Transactional
    public void removeMember(UUID roomId, UUID userId) {
        memberRepo.findByRoomIdAndUserId(roomId, userId)
                .ifPresent(memberRepo::delete);
    }

    @Override
    @Transactional
    public void deactivateRoom(UUID roomId) {
        ChatRoomEntity room = findRoomById(roomId);
        room.setActive(false);
        roomRepo.save(room);
    }

    @Override
    @Transactional
    public void deleteRoomForGroup(UUID groupId) {
        roomRepo.findByGroupId(groupId).ifPresent(room -> {
            UUID roomId = room.getId();
            memberRepo.clearLastReadMessagesByRoomId(roomId);
            reactionRepo.deleteAllByRoomId(roomId);
            messageRepo.deleteAllByRoomId(roomId);
            memberRepo.deleteAllByRoomId(roomId);
            roomRepo.delete(room);
        });
    }
}
