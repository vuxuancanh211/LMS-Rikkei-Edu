package project.lms_rikkei_edu.modules.chat.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import project.lms_rikkei_edu.modules.chat.entity.ChatMessageEntity;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequest {
    @Size(max = 5000, message = "Nội dung tối đa 5000 ký tự")
    private String content;

    private ChatMessageEntity.MessageType messageType = ChatMessageEntity.MessageType.TEXT;

    private UUID replyToId;

    @Size(max = 2048, message = "URL đính kèm quá dài")
    private String attachmentUrl;

    @Size(max = 255)
    private String attachmentName;

    private Long attachmentSizeBytes;
}
