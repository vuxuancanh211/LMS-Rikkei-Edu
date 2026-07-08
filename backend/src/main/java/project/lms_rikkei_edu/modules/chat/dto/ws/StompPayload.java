package project.lms_rikkei_edu.modules.chat.dto.ws;

import lombok.*;
import project.lms_rikkei_edu.modules.chat.dto.response.ChatMessageResponse;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StompPayload {
    // CHAT_MESSAGE | MESSAGE_EDITED | MESSAGE_DELETED
    // REACTION_ADDED | REACTION_REMOVED | USER_JOINED | USER_LEFT
    private String event;
    private UUID roomId;

    // Dùng cho CHAT_MESSAGE | MESSAGE_EDITED | MESSAGE_DELETED
    private ChatMessageResponse message;

    // Dùng cho REACTION event
    private UUID messageId;
    private String emoji;
    private Map<String, Long> reactions;

    // Dùng cho JOIN/LEAVE event
    private UUID userId;
    private String userName;

    private OffsetDateTime timestamp;
}
