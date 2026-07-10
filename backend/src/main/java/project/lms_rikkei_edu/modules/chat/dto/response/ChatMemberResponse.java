package project.lms_rikkei_edu.modules.chat.dto.response;

import lombok.*;
import project.lms_rikkei_edu.modules.chat.entity.ChatRoomMemberEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMemberResponse {
    private UUID userId;
    private String fullName;
    private String avatarUrl;
    private ChatRoomMemberEntity.MemberRole role;
    private OffsetDateTime joinedAt;
}
