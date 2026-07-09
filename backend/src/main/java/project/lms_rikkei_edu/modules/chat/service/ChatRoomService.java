package project.lms_rikkei_edu.modules.chat.service;

import project.lms_rikkei_edu.modules.chat.dto.response.ChatRoomResponse;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomEntity;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomMemberEntity;
import project.lms_rikkei_edu.modules.group.entity.StudyGroupEntity;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;

import java.util.List;
import java.util.UUID;

public interface ChatRoomService {

    List<ChatRoomResponse> getMyRooms(UUID userId);

    ChatRoomResponse getRoomDetail(UUID roomId, UUID userId);

    ChatRoomEntity createRoomForGroup(StudyGroupEntity group, UserEntity instructor);

    ChatRoomEntity getOrCreateRoomForGroup(StudyGroupEntity group, UserEntity instructor);

    void addMember(UUID roomId, UserEntity user, ChatRoomMemberEntity.MemberRole role);

    void markAsRead(UUID roomId, UUID messageId, UUID userId);

    ChatRoomEntity findRoomById(UUID roomId);

    void validateMember(UUID roomId, UUID userId);

    void removeMember(UUID roomId, UUID userId);

    void deactivateRoom(UUID roomId);

    void deleteRoomForGroup(UUID groupId);
}
