package project.lms_rikkei_edu.modules.chat.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomResponse {
    private UUID id;
    private String name;
    private UUID groupId;
    private String groupName;
    private boolean active;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime createdAt;
    private ChatMessageResponse lastMessage;
    private int unreadCount;
    private List<ChatMemberResponse> members;
}
