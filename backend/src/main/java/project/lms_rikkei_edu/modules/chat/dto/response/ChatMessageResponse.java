package project.lms_rikkei_edu.modules.chat.dto.response;

import lombok.*;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {
    private UUID id;
    private UUID roomId;

    // Sender
    private UUID senderId;
    private String senderName;
    private String senderAvatar;

    private ChatMessageEntity.MessageType messageType;
    private String content;

    // Attachment
    private String attachmentUrl;
    private String attachmentName;
    private Long attachmentSizeBytes;

    // Reply
    private UUID replyToId;
    private String replyToContent;
    private String replyToAttachmentName;
    private String replyToSenderName;

    private boolean edited;
    private OffsetDateTime editedAt;
    private boolean deleted;
    private OffsetDateTime createdAt;

    private Map<String, Long> reactions;
}
